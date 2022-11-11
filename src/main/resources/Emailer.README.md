### Конфигурация

Плагин выполняет отсылку уведомлений по smtp-протоколу. Конфигурация доступа к smtp-серверу выполняется один раз, при инициализации плагина.

Для успешной работы в основном конфиге logdoc должна присутствовать следующая секция с обязательными параметрами:
```hocon
logdoc.plugins.pipes.org.logdoc.pipes.Emailer {
  sender {
    email = "email@logdoc.org"
    name = "Auto Sender"
  }

  smtp {
    host = "host.com"
    port = 695
    timeout = 3000 // опционально. дефолт - 3000

    ssl = true // дефолт - false
    ssl { // опционально
      factory = "javax.net.ssl.SSLSocketFactory" // опционально. дефолт -- SSLSocketFactory
      
      tls = true // дефолт - false
      tls.protocols = "TLSv1.2,TLSv1.3" // опционально. дефолт - TLSv1.2
    }
    
    auth { // опционально
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
