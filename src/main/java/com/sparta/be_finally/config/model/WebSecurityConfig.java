package com.sparta.be_finally.config.model;

import com.sparta.be_finally.config.jwt.JwtAuthFilter;
import com.sparta.be_finally.config.jwt.JwtUtil;
import com.sparta.be_finally.config.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity // 스프링 Security 지원을 가능하게 함
@EnableGlobalMethodSecurity (securedEnabled = true) // @Secured 어노테이션 활성화
@RequiredArgsConstructor
public class WebSecurityConfig {
     private final JwtUtil jwtUtil;
     private final UserDetailsServiceImpl userDetailsService;
     
     @Bean // 비밀번호 암호화 기능 등록
     public PasswordEncoder passwordEncoder() {
          return new BCryptPasswordEncoder();
     }
     
     @Bean
     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
          // cors 설정
          http.cors();
          // CSRF 설정
          http.csrf().disable();
          
          // 기본 설정인 Session 방식은 사용하지 않고 JWT 방식을 사용하기 위한 설정
          http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
          http.authorizeRequests()
               // 토큰검증 필요없는 페이지 설정
//               .antMatchers(HttpMethod.GET,"/actuator/**").permitAll()
               .antMatchers("/user/**").permitAll()
               .antMatchers("/api/doc").permitAll()

               .antMatchers("/swagger-ui/**").permitAll() //스웨거 권한설정 X
               .antMatchers("/swagger-resources/**").permitAll() //스웨거 권한설정 X
               .antMatchers("/swagger-ui.html").permitAll() //스웨거 권한설정 X
               .antMatchers("/v2/api-docs").permitAll() //스웨거 권한설정 X
               .antMatchers("/v3/api-docs").permitAll() //스웨거 권한설정 X
               .antMatchers("/webjars/**").permitAll() //스웨거 권한설정 X
               .anyRequest().authenticated()
               //서버는 JWT 토큰을 검증하고 토큰의 정보를 사용하여 사용자의 인증을 진행해주는 Spring Security 에 등록한 JwtAuthFilter 를 사용하여 인증/인가를 처리한다.
               .and().addFilterBefore(new JwtAuthFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
          
          // 접근 제한 페이지 이동 설정
//          http.exceptionHandling().accessDeniedPage("/api/user/forbidden");
          
          return http.build();
     }

     @Bean
     public CorsConfigurationSource corsConfigurationSource(){

          CorsConfiguration config = new CorsConfiguration();

          config.addAllowedOrigin("http://localhost:3000");
//          https://dev.djcf93g3uh9mz.amplifyapp.com
          config.addAllowedOrigin("https://dev.djcf93g3uh9mz.amplifyapp.com");

          config.addExposedHeader(JwtUtil.AUTHORIZATION_HEADER);

          config.addAllowedMethod("*");

          config.addAllowedHeader("*");

          config.setAllowCredentials(true);

          config.validateAllowCredentials();

          UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
          source.registerCorsConfiguration("/**", config);

          return source;

     }
}