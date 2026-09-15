package duocuc.cl.rodrigo.carniverocrud.controller.config;

import duocuc.cl.rodrigo.carniverocrud.controller.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(
            UserDetailsService userDetailsService,
            JwtAuthFilter jwtAuthFilter
    ) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> localTokenFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    private AuthorizationDecision azureAccess(org.springframework.security.core.Authentication authentication,
                                               String scope, boolean admin) {
        boolean azure = authentication instanceof JwtAuthenticationToken && authentication.isAuthenticated();
        boolean hasScope = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("SCOPE_" + scope));
        boolean hasRole = !admin || authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return new AuthorizationDecision(azure && hasScope && hasRole);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(authenticationProvider());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken>
                    jwtAuthenticationConverter
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authenticationProvider(authenticationProvider())

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(
                                (request, response, exception) -> {
                                    response.setStatus(401);
                                    response.setContentType(
                                            "application/json;charset=UTF-8"
                                    );
                                    response.getWriter().write(
                                            "{\"message\":\"Debes iniciar sesion con un token valido.\"}"
                                    );
                                }
                        )
                        .accessDeniedHandler(
                                (request, response, exception) -> {
                                    response.setStatus(403);
                                    response.setContentType(
                                            "application/json;charset=UTF-8"
                                    );
                                    response.getWriter().write(
                                            "{\"message\":\"No tienes permisos para esta operacion.\"}"
                                    );
                                }
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // LOGIN / REGISTRO PROPIO
                        .requestMatchers(
                                HttpMethod.POST,
                                "/user/api/login"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/user/api/register"
                        ).permitAll()

                        // CATÁLOGO PÚBLICO
                        .requestMatchers(
                                HttpMethod.GET,
                                "/planta/api/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/images/**"
                        ).permitAll()

                        // EVIDENCIA AZURE
                        .requestMatchers(
                                HttpMethod.GET,
                                "/auth/azure-check"
                        ).access((authentication, context) -> azureAccess(authentication.get(), "Carnivario.Read", false))

                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/azure-admin-check"
                        ).access((authentication, context) -> azureAccess(authentication.get(), "Carnivario.Write", true))

                        // PERFIL
                        .requestMatchers(
                                HttpMethod.GET,
                                "/user/api/me"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/user/api/update"
                        ).authenticated()

                        // ADMIN USUARIOS
                        .requestMatchers(
                                HttpMethod.GET,
                                "/user/api"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/user/api"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/user/api/*/role"
                        ).hasRole("ADMIN")

                        // ADMIN PLANTAS
                        .requestMatchers(
                                HttpMethod.POST,
                                "/planta/api"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/planta/api/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/planta/api/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/uploads/api", "/api/uploads"
                        ).hasRole("ADMIN")

                        // COMPRA / CHECKOUT
                        .requestMatchers(HttpMethod.GET, "/purchase/api").hasRole("ADMIN")
                        .requestMatchers(
                                "/purchase/api/**"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/checkout/api"
                        ).authenticated()

                        .anyRequest().authenticated()
                )

                // JWT AZURE
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        // JWT LOCAL POR X-Local-Token
        http.addFilterBefore(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        http.headers(headers ->
                headers.frameOptions(frameOptions ->
                        frameOptions.disable()
                )
        );

        return http.build();
    }
}
