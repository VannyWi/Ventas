package com.giovi.demo.config;

import org.springframework.beans.factory.annotation.Autowired; // Importante
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 1. INYECTAMOS TU MANEJADOR DE ÉXITO
    @Autowired
    private CustomSuccessHandler successHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/registrar", "/css/**", "/js/**", "/images/**").permitAll() // Público
                .anyRequest().authenticated() // Lo demás requiere login
            )
            .formLogin(login -> login
                .loginPage("/login") 
                // 2. CAMBIAMOS 'defaultSuccessUrl' POR 'successHandler'
                .successHandler(successHandler) 
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
            
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Se mantiene el hash seguro
    }
}