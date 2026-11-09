package br.com.wezttech.prospectgt.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TelasController {

  @GetMapping("/")
  String home() {
    return "app";
  }

  @GetMapping("/login")
  String login() {
    return "login";
  }
}
