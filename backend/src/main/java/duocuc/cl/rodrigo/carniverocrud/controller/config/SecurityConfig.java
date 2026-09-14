package duocuc.cl.rodrigo.carniverocrud.controller.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import duocuc.cl.rodrigo.carniverocrud.controller.security.JwtAuthFilter;
import duocuc.cl.rodrigo.carniverocrud.controller.security.OAuth2LoginSuccessHandler;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    public SecurityConfig(UserDetailsService userDetailsService, JwtAuthFilter jwtAuthFilter, OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder())
                .and()
                .build();
    }
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }




    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authenticationProvider(authenticationProvider())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Debes iniciar sesion con un token valido.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"No tienes permisos para esta operacion.\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 1. AUTENTICACIÓN (PÚBLICO)
                        .requestMatchers(HttpMethod.POST, "/user/api/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/user/api/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/planta/api/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/uploads").permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/user/api/me").authenticated()

                        // 2. ADMINISTRACIÓN
                        .requestMatchers(HttpMethod.GET, "/user/api").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/user/api").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/user/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/planta/api").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/planta/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/planta/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/uploads/api", "/api/uploads").hasRole("ADMIN")

                        // 3. TRANSACCIONAL (PROTEGIDO - REQUIERE TOKEN)
                        .requestMatchers(HttpMethod.POST, "/purchase/api").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/planta/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/planta/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/checkout/api").authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler));

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));


        return http.build();
    }

}