- [Определение устройства](#определение-устройства)
  - [Устройства на Android 9](#устройства-на-android-9)
  - [Устройства на Android 13](#устройства-на-android-13)
- [Инструкции по настройке](#инструкции-по-настройке)
  - [Инструкция для Android 13](#инструкция-для-android-13)
    - [1. Включение режима разработчика](#1-включение-режима-разработчика)
    - [2. Подключение через ADB AppControl](#2-подключение-через-adb-appcontrol)
  - [Инструкция для Android 9](#инструкция-для-android-9)
    - [1. Подготовка через Sber Studio](#1-подготовка-через-sber-studio)
    - [2А. Через ADB TV (простой способ)](#2а-через-adb-tv-простой-способ)
    - [2Б. Через компьютер](#2б-через-компьютер)
- [Решение проблем](#решение-проблем)
  - [ADB не включается:](#adb-не-включается)
  - [Проблемы подключения:](#проблемы-подключения)
- [Альтернативный метод для YouTube](#альтернативный-метод-для-youtube)
  - [1. Настройка Sidestep](#1-настройка-sidestep)
  - [2. Настройка SmartTube](#2-настройка-smarttube)

# Определение устройства

## Устройства на Android 9

- SberBox:
  - SBDV-00001, SBDV-00002 (все модификации)
  - SBDV-00004, SBDV-00004C
- OKKO SmartBox (с прошивкой SberBox)
- SberBoxTime
- SberBoxTop
- SberPortal
- Sber TV (кроме моделей на Android 13)

## Устройства на Android 13

- SBDV-00004V (обновленный)
- SBDV-00004Р (обновленный)
- SBDV-00005
- SBDV-00006 (SberBox 2)

# Инструкции по настройке

## Инструкция для Android 13

> ⚠️ На этих устройствах работает только подключение через компьютер

### 1. Включение режима разработчика

- Откройте настройки Android
- Перейдите: Настройки устройства → Об устройстве
- Найдите **Сборка ОС** и нажмите на этот пункт несколько раз до появления сообщения об активации режима разработчика
- Вернитесь назад и перейдите в **Для разработчиков**
- Включите **Отладка по Wi-Fi**
- Запомните показанные *IP-адрес* и *порт*

### 2. Подключение через ADB AppControl

- Установите [ADB AppControl](https://adbappcontrol.com/ru/#download) на компьютер
- Введите *IP-адрес* и *порт* из настроек устройства
- Нажмите на значок **Wi-Fi** для подключения
- На вкладке **Консоль** выполните команды:

```
  shell pm grant io.github.metetv2021cloud.sidestep android.permission.WRITE_SECURE_SETTINGS
  shell appops set io.github.metetv2021cloud.sidestep WRITE_SETTINGS allow
  shell appops set io.github.metetv2021cloud.sidestep ACTIVATE_VPN allow
```

## Инструкция для Android 9

### 1. Подготовка через Sber Studio

- Войдите на [developers.sber.ru/studio](https://developers.sber.ru/studio/settings/devices) с вашим Сбер ID
- Нажмите "**+ добавить**" и укажите:
- Серийный номер (16 символов из настроек), Учтите: все буквы заглавные, O - это цифра 0
- Тип устройства (для OKKO выберите "OKKO Smart Box")
- Включите **функция ADB**

### 2А. Через ADB TV (простой способ)

- Установите [ADB TV](https://adbappcontrol.com/tv/download/?r=latest&lang=ru)
- На вкладке **Консоль** выполните:

```
  pm grant io.github.metetv2021cloud.sidestep android.permission.WRITE_SECURE_SETTINGS
  appops set io.github.metetv2021cloud.sidestep WRITE_SETTINGS allow
  appops set io.github.metetv2021cloud.sidestep ACTIVATE_VPN allow
```

### 2Б. Через компьютер

- Установите [ADB AppControl](https://adbappcontrol.com/ru/#download)
- Введите *IP-адрес* устройства и *порт* 5555
- На вкладке **Консоль** выполните:

```
  shell pm grant io.github.metetv2021cloud.sidestep android.permission.WRITE_SECURE_SETTINGS
  shell appops set io.github.metetv2021cloud.sidestep WRITE_SETTINGS allow
  shell appops set io.github.metetv2021cloud.sidestep ACTIVATE_VPN allow
```

# Решение проблем

## ADB не включается:

- Проверьте *тип устройства* и *серийный номер*
- Убедитесь, что устройство в сети
- Проверьте *Сбер ID*

## Проблемы подключения:

- Проверьте *IP-адрес* (может меняться)
- Устройства должны быть в одной сети
- Проверьте настройки роутера

# Альтернативный метод для YouTube

## 1. Настройка Sidestep

- В Sidestep:
  - На главном экране нажмите **Режим** и выберите **Прокси**
  - Порт по умолчанию — *1080* (его можно изменить в **Настройки → Адрес прокси → Порт**)
  - Нажмите большую кнопку **Подключить**

## 2. Настройка SmartTube

- Установите вариант **arm64_v8a** [SmartTube](https://github.com/yuliskov/SmartTube/releases/latest)
- В настройках SmartTube:
  - Общие → Интернет-цензура
  - Включите "Использовать веб-прокси"
    - Имя: *127.0.0.1*
    - Порт: *1080*
    - Тип: *SOCKS*
  - Нажмите **Тест** и **ОК**
