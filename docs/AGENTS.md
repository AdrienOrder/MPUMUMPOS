# MPUMUMPOS - Мобильное приложение метеостанции

## 1. Общая архитектура приложения

Приложение построено на принципе разделения ответственности (SOLID). Выделяются следующие уровни:

- Уровень пользовательского интерфейса (UI) - экраны (Activity), с которыми взаимодействует пользователь.
- Уровень бизнес-логики - управление Bluetooth-соединением, обработка команд, парсинг данных, логика работы с хранилищем файлов.
- Уровень данных - локальное хранилище (SQLite база данных и файловая система) для сохранения информации об устройствах и загруженных CSV-файлах.
- Уровень связи - модуль для работы с Bluetooth-модулем HC-05 (отправка/приём команд, чтение потоковых данных).

Все экраны связаны через навигационное меню (нижняя панель), а также через явные переходы по нажатию на элементы.

## 2. Описание экранов и их функций

### 2.1. Главный экран (MainActivity)

Назначение: подключение к Bluetooth-устройству, просмотр и изменение его рабочих параметров.

Расположение: `app/src/main/kotlin/com/mobileapp/MainActivity.kt`

Кнопки (ID в layout/activity_main.xml):

- btnConnect - Подключение/отключение к устройству
- btnEditParams - Изменение интервала и времени старта
- btnSyncTime - Синхронизация текущего времени
- btnDownloadStorage - Скачать данные в хранилище

Навигация:

- btnHome - Возврат на главный экран
- btnLogs - Переход к логам
- btnFiles - Переход к списку файлов

Основные поля:

- intervalEditText - Интервал измерений
- startTimeEditText - Время старта
- deviceTimeText - Текущее время устройства
- tvDeviceStatus - Статус подключения
- tvDeviceName - Имя устройства

Команды для устройства (BluetoothCommands.kt):

- SET interval <минуты> - установка интервала
- SET start <ДД:ЧЧ:ММ> - установка времени старта
- SET time <timestamp> - синхронизация времени
- GET interval - запрос интервала
- GET start - запрос времени старта
- GET time - запрос времени устройства

### 2.2. Экран логов (LogsActivity)

Назначение: отображение истории событий, обмена командами и ошибок.

Расположение: `app/src/main/kotlin/com/mobileapp/LogDisplayActivity.kt`

Кнопки (layout/activity_logs.xml):

- btnClear - Очистить логи
- btnCopy - Скопировать логи

Навигация:

- btnHome - Возврат на главный экран
- btnLogs - Возврат к логам
- btnFiles - Переход к списку файлов

Текстовые поля:

- logLabel - Заголовок "Лог операций"

### 2.3. Экран выбора Bluetooth-устройства (DeviceListActivity)

На��начение: выбор из списка сопряжённых Bluetooth-адаптеров.

Расположение: `app/src/main/kotlin/com/mobileapp/DeviceListActivity.kt`

Кнопки (layout/activity_device_list.xml):

- btnRefresh - Обновить список устройств

Основные функции:

- Отображение списка сопряжённых устройств
- Запрос разрешений Bluetooth и местоположения (Android 12+)
- Обработка ошибок (Bluetooth выключен, нет устройств)
- Возврат выбранного устройства в MainActivity

### 2.4. Экран "Хранилище" (StorageActivity)

Назначение: доступ ко всем устройствам и сохранённым CSV-файлам.

Расположение: `app/src/main/kotlin/com/mobileapp/StorageActivity.kt`

Кнопки (layout/activity_storage.xml):

- btnImport - Импорт из Загрузок

Навигация:

- btnHome - Возврат на главный экран
- btnLogs - Переход к логам
- btnStorage - Возврат к хранилищу

Основные функции:

- Список устройств с количеством файлов
- Удаление устройства (btnDelete в item_device.xml)
- Переход к списку файлов (btnOpen)
- Импорт CSV из папки Загрузки

### 2.5. Экран списка CSV-файлов (FileListActivity)

Назначение: список файлов для выбранного устройства.

Расположение: `app/src/main/kotlin/com/mobileapp/FileListActivity.kt`

Кнопки в элементах списка (layout/item_file.xml):

- btnVisualize - Построить график
- btnDownload - Скачать файл
- btnDelete - Удалить файл

Навигация:

- btnHome - Возврат на главный экран
- btnLogs - Переход к логам
- btnStorage - Переход к хранилищу

Основные поля:

- fileName - Имя файла
- fileSize - Размер файла
- downloadedAt - Дата загрузки

### 2.6. Экран визуализации (VisualizationActivity)

Назначение: выбор параметров и построение графиков.

Расположение: `app/src/main/kotlin/com/mobileapp/VisualizationActivity.kt`

Кнопки (layout/activity_visualization.xml):

- btnShowChart - Показать график
- btnShowTable - Показать таблицу
- btnExportPdf - Экспорт в PDF
- btnFromDate - Выбор начальной даты
- btnToDate - Выбор конечной даты
- btnDay - Выбор дня
- btnMonth - Выбор месяца
- btnYear - Выбор года
- btnApply - Применить фильтр
- btnClearFilter - Очистить фильтр

Навигация:

- btnHome - Возврат на главный экран
- btnLogs - Переход к логам
- btnStorage - Переход к хранилищу

Основные функции:

- Выбор измерений (чекбоксы)
- Построение графика
- Ограничение по оси X (диапазон дат)
- Выбор дня/месяца/года (сортировка по возрастанию)

### 2.7. Экран графиков (ChartActivity)

Назначение: отображение графиков с использованием MPAndroidChart.

Расположение: `app/src/main/kotlin/com/mobileapp/ChartActivity.kt`

Кнопки (layout/activity_chart.xml):

- btnBack - Назад
- btnRotate - Повернуть график

### 2.8. Экран таблицы (TableActivity)

Назнач��ние: отображение данных в табличном виде.

Расположение: `app/src/main/kotlin/com/mobileapp/TableActivity.kt`

Кнопки (layout/activity_table.xml):

- btnBack - Назад
- btnRotate - Повернуть таблицу

## 3. Взаимодействие экранов и навигация

Навигационное меню (нижняя панель) присутствует на экранах:

- MainActivity
- LogsActivity
- StorageActivity
- FileListActivity
- VisualizationActivity

Кнопки навигации:

- btnHome - Возврат на главный экран
- btnLogs - Переход к логам
- btnFiles / btnStorage - Переход к хранилищу

Переходы между экранами:

- MainActivity -> DeviceListActivity (выбор устройства)
- StorageActivity -> FileListActivity (список файлов устройства)
- FileListActivity -> VisualizationActivity (btnVisualize)
- VisualizationActivity -> ChartActivity (btnShowChart)
- VisualizationActivity -> TableActivity (btnShowTable)

## 4. Хранение данных

### 4.1. База данных SQLite

Расположение: `app/src/main/kotlin/com/mobileapp/data/StorageDatabaseManager.kt`

Таблица devices (устройства):

- id - INTEGER PRIMARY KEY
- name - TEXT (имя устройства)
- mac_address - TEXT UNIQUE (MAC-адрес)
- first_connected - INTEGER (timestamp первого подключения)
- last_active - INTEGER (timestamp последней активности)

Таблица csv_files (файлы):

- id - INTEGER PRIMARY KEY
- file_name - TEXT (имя файла)
- file_path - TEXT (путь к файлу)
- device_id - INTEGER FOREIGN KEY
- downloaded_at - INTEGER (timestamp загрузки)
- file_size - INTEGER (размер в байтах)

### 4.2. Файловая система

- CSV-файлы сохраняются во внутренней директории приложения
- Путь: `context.filesDir/device_<id>/`
- При удалении устройства удаляются все связанные файлы

### 4.3. Логи

- Хранятся в памяти (только текущая сессия)
- Логирование через LogStorageManager

## 5. Парсинг CSV данных

Расположение: `app/src/main/kotlin/com/mobileapp/data/CsvDataParser.kt`

### 5.1. Поддерживаемые форматы даты

- M/d/yyyy (например: 2024/1/4)
- d/M/yyyy
- yyyy-MM-dd
- dd.MM.yyyy

### 5.2. Поддерживаемые форматы времени

- H:mm (например: 11:00)
- H:mm:ss
- hh:mm:ss a
- hh:mm a

### 5.3. Обработка данных

- Пропуски данных обозначаются как "-" или "(NO REPLY)"
- Такие значения пропускаются при построении графиков
- Автоматическое определение колонок с данными (исключая Date, Time, Hour, Data)

### 5.4. Результат парсинга

CsvFileInfo:

- fileName - имя файла
- headers - все заголовки
- dataColumns - колонки с числовыми данными
- dataPoints - список точек данных (timestamp + значения)
- lineCount - количество строк

CsvDataPoint:

- timestamp - метка времени
- values - Map<имя параметра, значение>

## 6. Bluetooth связь

Расположение: `app/src/main/kotlin/com/mobileapp/BluetoothManager.kt`

### 6.1. Подключение

- UUID HC-05: 00001101-0000-1000-8000-00805F9B34FB
- Синглтон (одно подключение)
- Асинхронное чтение в отде��ьн��м потоке

### 6.2. Отправка команд

- Преобразование команд в байты
- Запись в OutputStream
- Логирование отправки

### 6.3. Приём данных

- BufferedReader для построчного чтения
- Callback на каждое received сообщение
- Автоматическое логирование

## 7. Импорт CSV файлов

### 7.1. Из внутреннего хранилища

- Файлы, загруженные с устройства, сохраняются в приложении
- Доступны через StorageActivity -> выбрать устройство -> FileListActivity

### 7.2. Из папки "Загрузки" Android

- StorageActivity -> btnImport
- Используется Storage Access Framework (SAF)
- Файл копируется во внутреннее хранилище
- Создаётся устройство "Imported" для ассоциации

Управление импортом: `app/src/main/kotlin/com/mobileapp/data/CsvImportManager.kt`

## 8. Структура проекта

```
app/src/main/kotlin/com/mobileapp/
  MainActivity.kt              - Главный экран
  LogDisplayActivity.kt        - Экран логов
  DeviceListActivity.kt      - Выбор Bluetooth-устройства
  StorageActivity.kt        - Хранилище
  FileListActivity.kt       - Список файлов
  VisualizationActivity.kt  - Визуализация
  ChartActivity.kt         - Графики
  TableActivity.kt         - Таблица
  BluetoothManager.kt      - Управление Bluetooth
  BluetoothCommands.kt    - Константы команд
  DialogManager.kt         - Диалоговые окна
  LogStorageManager.kt      - Логирование
  DeviceAdapter.kt         - Адаптер устройств (item_device.xml)
  FileAdapter.kt          - Адаптер файлов (item_file.xml)
  ParamAdapter.kt          - Адаптер параметров

  data/
    CsvDataParser.kt           - Парсинг CSV
    CsvImportManager.kt       - Импорт файлов
    StorageDatabaseManager.kt  - База данных
    Device.kt               - Модель устройства
    CsvFile.kt              - Модель файла
    ParamBounds.kt          - Границы параметров
```

## 9. Зависимости

- AndroidX AppCompat
- AndroidX RecyclerView
- AndroidX ConstraintLayout
- MPAndroidChart (графики)
- Material Components

## 10. Параметры измерений

Параметры определяются динамически из заголовков CSV-файла. Типичные параметры:

- TempAir - Температура воздуха (C)
- HumAir - Влажность воздуха (%)
- PressAir - Атмосферное давление (hPa)
- TempSoil - Температура почвы (C)
- MoinsSoil - Влажность почвы (%)
- WindSpeed - Скорость ветра (м/с)
- WindGust - Порыв ветра (м/с)
- WindDirect - Направление ветра (градусы)
- Solar - Солнечная радиация (Вт/м2)

Количество и тип параметров могут варьироваться в зависимости от конфигурации метеостанции. Приложение автоматически определяет все числовые колонки из CSV-файла.