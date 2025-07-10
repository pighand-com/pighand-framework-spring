package com.pighand.framework.spring.http.exchange;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.util.Set;

/**
 * HttpExchange注册器
 *
 * <p>启用方式：@Import({HttpExchangeRegister.class})
 *
 * @author wangshuli
 */
@Slf4j
public class HttpExchangeRegister implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
    private ApplicationContext applicationContext;

    /**
     * 查找启动类包名
     *
     * @return
     */
    private String findApplicationPackageName() {
        ConfigurableListableBeanFactory beanFactory =
            (ConfigurableListableBeanFactory)applicationContext.getAutowireCapableBeanFactory();

        String[] beanNames = beanFactory.getBeanNamesForAnnotation(SpringBootApplication.class);

        if (beanNames.length > 0) {
            String beanName = beanNames[0];
            String className = beanFactory.getBeanDefinition(beanName).getBeanClassName();
            return ClassUtils.getPackageName(className);
        }

        return null;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 扫描带有@HttpExchange的接口，使用默认WebClient生成代理，以驼峰命名方式注入到spring容器
     *
     * @param registry
     * @throws BeansException
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassPathScanningCandidateInterfaceProvider provider = new ClassPathScanningCandidateInterfaceProvider();
        provider.addIncludeFilter(new AnnotationTypeFilter(HttpExchange.class));

        String packageName = findApplicationPackageName();

        if (packageName == null) {
            throw new RuntimeException("can not find application package name");
        }

        Set<BeanDefinition> httpExchangeBeans = provider.findCandidateComponents(packageName);

        for (BeanDefinition httpExchangeBean : httpExchangeBeans) {
            Class clz = null;
            try {
                clz = Class.forName(httpExchangeBean.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            RestClient.Builder clientBuilder = RestClient.builder();

            // 如果日志级别是 info，则添加日志拦截器
            if (log.isInfoEnabled()) {
                clientBuilder.requestInterceptor(new LoggingInterceptor());
            }

            RestClient client = clientBuilder.build();

            Object bean =
                HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(clz);

            RootBeanDefinition beanDefinition = new RootBeanDefinition(clz, () -> bean);

            String className = httpExchangeBean.getBeanClassName();
            String[] split = className.split("\\.");
            String beanName = split[split.length - 1];
            beanName = beanName.substring(0, 1).toLowerCase() + beanName.substring(1);

            registry.registerBeanDefinition(beanName, beanDefinition);
        }
    }

    /**
     * 自定义日志拦截器
     */
    static class LoggingInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
            // 打印请求日志
            log.info("Request: URI={}, Method={}, Headers={}, Body={}", request.getURI(), request.getMethod(),
                request.getHeaders(), new String(body));

            ClientHttpResponse response = execution.execute(request, body);

            // 打印响应日志
            log.info("Response: Status Code={}, headers={}", response.getStatusCode(), response.getHeaders());

            return response;
        }
    }
}
