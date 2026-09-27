package com.ceramic.platform.config;

import com.ceramic.platform.common.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    /** 上传目录固定路径；未配置时回退到工作目录下的 uploads（避免不同启动方式读到不同目录） */
    @Value("${app.upload.dir:}")
    private String uploadDir;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
        // 目录型 location 必须以 "/" 结尾，否则与文件名拼接时缺分隔符导致静态资源 404/500
        String uploadLoc = Paths.get(resolveUploadBase()).toAbsolutePath().toUri().toString();
        if (!uploadLoc.endsWith("/")) {
            uploadLoc += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLoc);
    }

    private String resolveUploadBase() {
        return (uploadDir == null || uploadDir.isBlank())
                ? Paths.get(System.getProperty("user.dir"), "uploads").toString()
                : uploadDir;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:5174")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/users/login", "/api/users/service/login", "/api/users/register",
                                     "/api/users/sms/send", "/api/users/sms/login",
                                     "/images/**", "/uploads/**");
    }

    @Override
    public void extendMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
        converters.add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }
}
