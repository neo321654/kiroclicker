# Документ дизайна

## Обзор

Android-приложение автокликер использует Android Accessibility Service для выполнения автоматических кликов и OpenCV для распознавания изображений на экране. Приложение состоит из пользовательского интерфейса для настройки и фонового сервиса для выполнения автоматизации.

## Архитектура

Приложение построено по архитектуре MVVM с использованием следующих компонентов:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   MainActivity  │────│  ConfigViewModel │────│ ConfigRepository│
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │              ┌─────────────────┐              │
         └──────────────│ AutoClickService │──────────────┘
                        └─────────────────┘
                                 │
                        ┌─────────────────┐
                        │ ImageMatcher    │
                        └─────────────────┘
```

### Основные слои:

1. **UI Layer** - Activities и Fragments для пользовательского интерфейса
2. **ViewModel Layer** - Управление состоянием UI и бизнес-логикой
3. **Service Layer** - Accessibility Service для выполнения кликов
4. **Repository Layer** - Управление данными и конфигурациями
5. **Utility Layer** - Вспомогательные классы для работы с изображениями

## Компоненты и интерфейсы

### 1. MainActivity
```kotlin
class MainActivity : AppCompatActivity() {
    // Основной экран приложения
    // Управляет навигацией между фрагментами
    // Проверяет разрешения Accessibility Service
}
```

### 2. ConfigFragment
```kotlin
class ConfigFragment : Fragment() {
    // Интерфейс для настройки автоклика
    // Выбор изображения, установка координат
    // Настройка параметров (интервал, повторения)
}
```

### 3. AutoClickService
```kotlin
class AutoClickService : AccessibilityService() {
    // Фоновый сервис для выполнения кликов
    // Захват скриншотов экрана
    // Поиск шаблона и выполнение кликов
    
    fun startAutoClick(config: ClickConfig)
    fun stopAutoClick()
    fun performClick(x: Int, y: Int)
}
```

### 4. ImageMatcher
```kotlin
class ImageMatcher {
    // Утилита для сравнения изображений
    // Использует OpenCV для поиска шаблона
    
    fun findTemplate(screenshot: Bitmap, template: Bitmap): Point?
    fun matchTemplate(source: Mat, template: Mat): MatchResult
}
```

### 5. ConfigRepository
```kotlin
class ConfigRepository {
    // Управление сохранением/загрузкой конфигураций
    // Работа с SharedPreferences и файловой системой
    
    fun saveConfig(config: ClickConfig): Boolean
    fun loadConfig(name: String): ClickConfig?
    fun getAllConfigs(): List<ClickConfig>
}
```

## Модели данных

### ClickConfig
```kotlin
data class ClickConfig(
    val id: String,
    val name: String,
    val templateImagePath: String,
    val clickX: Int,
    val clickY: Int,
    val intervalMs: Long,
    val repeatCount: Int, // -1 для бесконечного режима
    val threshold: Double = 0.8 // Порог совпадения для распознавания
)
```

### MatchResult
```kotlin
data class MatchResult(
    val found: Boolean,
    val confidence: Double,
    val location: Point?
)
```

### AutoClickState
```kotlin
sealed class AutoClickState {
    object Idle : AutoClickState()
    object Searching : AutoClickState()
    object Clicking : AutoClickState()
    object Waiting : AutoClickState()
    data class Error(val message: String) : AutoClickState()
    data class Completed(val clickCount: Int) : AutoClickState()
}
```

## Технические детали

### Accessibility Service
- Использует `AccessibilityService` для получения разрешений на взаимодействие с экраном
- Метод `dispatchGesture()` для выполнения кликов
- `takeScreenshot()` для захвата экрана (API 28+)

### Распознавание изображений
- OpenCV Android SDK для сравнения изображений
- Алгоритм Template Matching для поиска шаблона
- Настраиваемый порог совпадения (threshold)

### Разрешения
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### Accessibility Service Declaration
```xml
<service
    android:name=".service.AutoClickService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

## Обработка ошибок

### Типы ошибок:
1. **Отсутствие разрешений** - Перенаправление в настройки системы
2. **Шаблон не найден** - Продолжение поиска или уведомление пользователя
3. **Ошибка захвата экрана** - Повторная попытка или остановка сервиса
4. **Ошибка выполнения клика** - Логирование и продолжение работы

### Стратегии обработки:
- Retry механизм для временных ошибок
- Graceful degradation при критических ошибках
- Подробное логирование для отладки
- Уведомления пользователя о состоянии системы

## Стратегия тестирования

### Unit Tests
- Тестирование ImageMatcher с различными изображениями
- Тестирование ConfigRepository для сохранения/загрузки
- Тестирование ViewModel логики

### Integration Tests
- Тестирование взаимодействия между компонентами
- Тестирование Accessibility Service функциональности
- Тестирование полного цикла автоклика

### UI Tests
- Тестирование пользовательского интерфейса
- Тестирование выбора изображений и установки координат
- Тестирование навигации между экранами

### Manual Testing
- Тестирование на различных устройствах и версиях Android
- Тестирование производительности при длительной работе
- Тестирование точности распознавания изображений