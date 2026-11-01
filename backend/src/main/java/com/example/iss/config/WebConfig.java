package com.example.iss.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves openapi.yaml (bundled at the classpath root, not under static/) at
 * /openapi.yaml, and ensures /docs resolves to the static docs page without a
 * trailing slash (PROMPT.md section 6, Step 6 decision 1). No CORS bean is
 * registered here: default CORS policy is "none" (same-origin only), per section 8.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi.yaml")
                .addResourceLocations("classpath:/openapi.yaml");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/docs").setViewName("forward:/docs/index.html");
    }
}
