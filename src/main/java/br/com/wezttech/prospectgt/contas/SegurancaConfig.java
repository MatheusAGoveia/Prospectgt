package br.com.wezttech.prospectgt.contas;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SegurancaConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(ContaRepository repository) {
    return username ->
      repository
        .findByUsername(username)
        .filter(x -> x.enabled)
        .map(x -> User.withUsername(x.username).password(x.passwordHash).roles(x.role).build())
        .orElseThrow(() -> new UsernameNotFoundException("Conta indisponível"));
  }

  @Bean
  SecurityFilterChain filter(HttpSecurity http) throws Exception {
    return http
      .authorizeHttpRequests(a ->
        a
          .requestMatchers("/login", "/assets/**", "/error")
          .permitAll()
          .requestMatchers("/api/admin/**")
          .hasRole("ADMIN")
          .anyRequest()
          .authenticated()
      )
      .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
      .logout(l -> l.logoutSuccessUrl("/login?logout"))
      .exceptionHandling(e ->
        e.defaultAuthenticationEntryPointFor(
          new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
          new AntPathRequestMatcher("/api/**")
        )
      )
      .headers(h ->
        h.contentSecurityPolicy(c ->
          c.policyDirectives(
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://tile.openstreetmap.org; connect-src 'self'; font-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'"
          )
        )
      )
      .build();
  }
}
