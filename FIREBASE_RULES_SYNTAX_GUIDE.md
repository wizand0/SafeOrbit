# Руководство по синтаксису Firebase Realtime Database Rules

## Поддерживаемые методы

### Основные методы
- `auth` - информация об аутентифицированном пользователе
- `root` - корневой узел базы данных
- `data` - текущие данные узла
- `newData` - новые данные, которые пытаются быть записаны

### Методы для навигации
- `root.child(path)` - получить дочерний узел
- `data.child(path)` - получить дочерний узел текущих данных
- `.val()` - получить значение узла
- `.hasChildren([keys])` - проверить наличие дочерних ключей

### Операторы сравнения
- `===`, `==`, `!=`, `!==` - операторы равенства
- `>`, `<`, `>=`, `<=` - операторы сравнения

## НЕ поддерживаемые методы

### Строковые методы
- `.length()` - не поддерживается в RTDB Rules
- `.substring()`, `.indexOf()`, `.charAt()` и другие строковые методы
- Для проверки длины строки используйте сравнение с пустой строкой: `newData.val() != ''`

### Массивы и коллекции
- `.length` для массивов - не поддерживается
- `.exists()` - не поддерживается в некоторых контекстах
- Для проверки существования значения используйте: `data.val() != null` или `data.val() == null`

### Другие ограничения
- Нельзя вызывать методы цепочкой: `data.child('key').exists()`
- Некоторые функции могут не работать в определенных контекстах

## Практические примеры

### Проверка наличия значения
```javascript
// Правильно
".write": "(data.val() == null)"

// Неправильно
".write": "!data.exists()"
```

### Проверка строк на пустоту
```javascript
// Правильно
".validate": "newData.isString() && newData.val() != ''"

// Неправильно
".validate": "newData.isString() && newData.val().length() > 0"
```

### Проверка числовых диапазонов
```javascript
// Правильно
".validate": "newData.isNumber() && newData.val() > 0 && newData.val() <= 3600000"
```

### Проверка типа данных
```javascript
// Поддерживаемые проверки типа
"newData.isString()"
"newData.isNumber()" 
"newData.isBoolean()"
"newData.isObject()"
```

## Безопасные шаблоны

### Только создание (не изменение)
```javascript
".write": "(data.val() == null)"
```

### Только однократное изменение
```javascript
".write": "(data.val() == false || data.val() == null) && newData.val() == true"
```

### Проверка принадлежности
```javascript
".read": "auth != null && root.child('owners').child($id).val() === auth.uid"
```

## Часто используемые шаблоны для SafeOrbit

### Создание сервера (только один раз)
```javascript
"ownerUid": {
  ".write": "(data.val() == null)"
}
```

### Pairing данные (только при создании)
```javascript
"pairing": {
  "tokenHash": {
    ".write": "(data.val() == null)"
  },
  "expiresAt": {
    ".write": "(data.val() == null)"
  }
}
```

### Однократное использование токена
```javascript
"consumed": {
  ".write": "auth != null && ... && (data.val() == false || data.val() == null) && newData.val() == true"
}
```

## Отладка

При возникновении ошибок типа "Function call on target that is not a function" проверьте:

1. Не используете ли вы неподдерживаемые методы (`.length()`, `.exists()`)
2. Не вызываете ли вы методы цепочкой, где это не поддерживается
3. Правильно ли используете операторы сравнения
4. Соответствуют ли типы данных ожидаемым

## Ресурсы

- [Официальная документация по RTDB Rules](https://firebase.google.com/docs/database/security/rules-conditions)
- [Правила валидации данных](https://firebase.google.com/docs/database/security/core-syntax#validation)
- [Примеры безопасных правил](https://firebase.google.com/docs/database/security/common-pitfalls)