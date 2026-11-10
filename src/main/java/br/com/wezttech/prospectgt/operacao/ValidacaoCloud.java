package br.com.wezttech.prospectgt.operacao;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("cloud")
public class ValidacaoCloud {

  @Bean
  static BeanFactoryPostProcessor exigirPostgresComTls(Environment environment) {
    return factory ->
      ConexaoSegura.validarJdbc(environment.getRequiredProperty("spring.datasource.url"));
  }
}
