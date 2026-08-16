package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.starter.repository.JpaPlusRepositoryFactoryBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 JPA Plus Repository 工厂定义接管的基础设施边界。
 */
class JpaPlusAutoConfigurationTest {

    @Test
    void repositoryFactoryReplacer_replacesOnlySpringDataDefaultFactory() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("defaultRepository", definition(JpaRepositoryFactoryBean.class.getName()));
        registry.registerBeanDefinition("customRepository", definition("example.CustomRepositoryFactoryBean"));

        BeanDefinitionRegistryPostProcessor processor =
                JpaPlusAutoConfiguration.jpaPlusRepositoryFactoryReplacer();
        processor.postProcessBeanDefinitionRegistry(registry);

        assertThat(registry.getBeanDefinition("defaultRepository").getBeanClassName())
                .isEqualTo(JpaPlusRepositoryFactoryBean.class.getName());
        assertThat(registry.getBeanDefinition("customRepository").getBeanClassName())
                .isEqualTo("example.CustomRepositoryFactoryBean");
    }

    private static GenericBeanDefinition definition(String beanClassName) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClassName(beanClassName);
        return definition;
    }
}
