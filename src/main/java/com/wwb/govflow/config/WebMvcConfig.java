package com.wwb.govflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 首页与控制台多别名映射，防止无后缀或单斜杠 404
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/index").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/index.html");
        registry.addViewController("/home").setViewName("forward:/index.html");

        // 接口文档多别名重定向（兼容传统 Swagger 习惯），彻底杜绝 404 Whitelabel
        registry.addViewController("/doc").setViewName("redirect:/doc.html");
        registry.addViewController("/docs").setViewName("redirect:/doc.html");
        registry.addViewController("/swagger").setViewName("redirect:/doc.html");
        registry.addViewController("/swagger-ui").setViewName("redirect:/doc.html");
        registry.addViewController("/swagger-ui.html").setViewName("redirect:/doc.html");
    }
}
