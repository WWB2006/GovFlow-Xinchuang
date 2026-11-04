package com.wwb.govflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

@Configuration
@EnableSwagger2WebMvc
public class Knife4jConfig {

    @Bean
    public Docket defaultApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .groupName("1.0.0-信创专版")
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.wwb.govflow.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("政务协同与信创审批流转服务 API 文档")
                .description("智慧政务协同与信创数据库平滑迁移工程：双数据库方言转换 (MySQL/达梦DM8)、Kafka消息削峰、分布式锁与审批流转")
                .termsOfServiceUrl("http://localhost:8087/")
                .contact(new Contact("王文博", "https://github.com/WWB2006", "2793814466@qq.com"))
                .version("1.0.0")
                .build();
    }
}
