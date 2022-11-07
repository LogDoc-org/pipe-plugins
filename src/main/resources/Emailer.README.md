### Конфигурация

Плагин выполняет отсылку уведомлений по smtp-протоколу. Конфигурация доступа к smtp-серверу выполняется один раз, при инициализации плагина.

Для успешной работы в основном конфиге logdoc должна присутствовать следующая секция с обязательными параметрами:
```hocon
logdoc.plugins.pipes.org.logdoc.pipes.Emailer {
  sender {
    email = "email@logdoc.com"
    name = "Auto Sender"
  }

  smtp {
    ssl = true
    tls = true
    host = "host.com"
    port = 695

    auth {
      user = "user"
      password = "password"
    }
  }
}
```

Также, там же могут присутствовать два опциональных параметра:

```hocon
logdoc.plugins.pipes.org.logdoc.pipes.Emailer {
  default_subject = "Logdoc notification"
  default_body = "Logdoc notification body"
}
```
