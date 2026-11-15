ByeByeDPI Android
<div align="center">
  <p>
    <img src="https://github.com/romanvht/ByeDPIAndroid/raw/master/.github/images/app.svg" alt="Логотип ByeDPI" width="200" />
  </p>
  <h1>ByeByeDPI Android</h1>
  <p>
    Русский |
    <a href="README-en.md">English</a> |
    <a href="README-tr.md">Türkçe</a>
  </p>
  <p>
    <a href="https://github.com/romanvht/ByeByeDPI/releases/latest"><img src="https://img.shields.io/github/v/release/romanvht/ByeByeDPI" alt="Latest Release" /></a>
    <a href="https://github.com/romanvht/ByeByeDPI/releases"><img src="https://img.shields.io/github/downloads/romanvht/ByeByeDPI/total" alt="Downloads" /></a>
    <a href="https://github.com/romanvht/ByeByeDPI/blob/master/LICENSE"><img src="https://img.shields.io/github/license/romanvht/ByeByeDPI" alt="License" /></a>
    <a href="https://github.com/romanvht/ByeByeDPI"><img src="https://img.shields.io/github/languages/code-size/romanvht/ByeByeDPI" alt="GitHub code size in bytes"/></a>
  </p>
</div>

Приложение для Android, которое локально запускает ByeDPI и перенаправляет весь трафик через него.

Для стабильной работы может потребоваться изменить настройки. Подробнее о различных настройках можно прочитать в [документации ByeDPI](https://github.com/hufrea/byedpi/blob/main/README.md).

Приложение не является VPN. Оно использует VPN-режим на Android для перенаправления трафика, но не передает ничего на удаленный сервер. Оно не шифрует трафик и не скрывает ваш IP-адрес.

У приложения есть единственный официальный сайт -> https://byebyedpi.xyz

---

### Возможности
* Автозапуск сервиса при старте устройства
* Сохранение списков параметров командной строки
* Улучшена совместимость с Android TV/BOX
* Раздельное туннелирование приложений
* Импорт/экспорт настроек

### Использование
* Для работы автозапуска активируйте пункт в настройках.
* Рекомендуется подключится один раз к VPN, чтобы принять запрос.
* После этого, при загрузке устройства, приложение автоматически запустит сервис в зависимости от настроек (VPN/Proxy)
* Комплексная инструкция от комьюнити [ByeByeDPI-Manual](https://byebyedpi.xyz)

### Сборка
1. Клонируйте репозиторий с сабмодулями:
```bash
git clone --recurse-submodules
```
2. Запустите скрипт сборки из корня репозитория:
```bash
./gradlew assembleRelease
```
3. APK будет в `app/build/outputs/apk/release/`

> P.S.: hev_socks5_tunnel не соберется под Windows, вам нужно будет использовать WSL

### Хеш подписи
SHA-256:
`77:45:10:75:AC:EA:40:64:06:47:5D:74:D4:59:88:3A:49:A6:40:51:FA:F3:2E:42:F7:18:F3:F9:77:7A:8D:FB`

### Зависимости
- [ByeDPI](https://github.com/hufrea/byedpi)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)

### Благодарность
- [hufrea](https://github.com/hufrea) - за [ByeDPI](https://github.com/hufrea/byedpi)
- [dovecoteescapee](https://github.com/dovecoteescapee) - за изначальную реализацию [ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid)
