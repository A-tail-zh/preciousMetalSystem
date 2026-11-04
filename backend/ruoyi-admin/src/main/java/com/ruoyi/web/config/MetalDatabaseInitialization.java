package com.ruoyi.web.config;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** RuoYi warms database-backed caches in @PostConstruct; migrate before those beans initialize. */
@Configuration
@Profile("metal")
public class MetalDatabaseInitialization {
    @Bean
    public static BeanFactoryPostProcessor metalMigrationDependencies() {
        return factory -> {
            for (String name : new String[]{"sysConfigServiceImpl", "sysDictTypeServiceImpl", "sysJobServiceImpl"}) {
                if (factory.containsBeanDefinition(name)) {
                    var definition = factory.getBeanDefinition(name);
                    String[] existing = definition.getDependsOn();
                    definition.setDependsOn(Stream.concat(
                        existing == null ? Stream.empty() : Arrays.stream(existing),
                        Stream.of("flywayInitializer")).distinct().toArray(String[]::new));
                }
            }
        };
    }
}
