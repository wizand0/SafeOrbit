# Инструкции по обновлению Firebase Realtime Database Rules

## Текущее состояние

Текущие правила в Firebase Realtime Database слишком широкие и небезопасные:

```json
{
  "rules": {
    "servers": {
      "$serverId": {
        ".write": "auth != null && auth.uid != null",
        ".read": "auth != null"
      }
    },
    "clients": {
      "$clientId": {
        ".read": "auth != null && auth.uid === $clientId",
        ".write": "auth != null && auth.uid === $clientId"
      }
    },
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid"
      }
    },
    "server_commands": {
      "$serverId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

## Новые безопасные правила

```json
{
  "rules": {
    ".read": false,
    ".write": false,
    
    "servers": {
      "$serverId": {
        ".read": "auth != null && (root.child('servers').child($serverId).child('ownerUid').val() === auth.uid || root.child('clients').child(auth.uid).child('linked_servers').child($serverId).val() === true)",
        
        ".write": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",
        
        "ownerUid": {
          ".read": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",
          ".write": "!data.child('ownerUid').exists()"  // только при создании
        },
        
        "pairing": {
          ".read": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",  // только владелец может читать pairing данные
          
          ".write": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid && !data.child('tokenHash').exists()",  // нельзя изменить токен после создания
          
          "tokenHash": {
            ".read": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",
            ".write": "!data.child('tokenHash').exists()"  // только при создании
          },
          
          "expiresAt": {
            ".read": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",
            ".write": "!data.child('expiresAt').exists()"  // только при создании
          },
          
          "consumed": {
            ".read": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",
            ".write": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid && (!data.exists() || data.val() == false) && newData.val() == true"  // только однократное изменение на true
          }
        },
        
        "location": {
          ".read": "auth != null && (root.child('servers').child($serverId).child('ownerUid').val() === auth.uid || root.child('clients').child(auth.uid).child('linked_servers').child($serverId).val() === true)",
          ".write": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid"
        },
        
        "app_notifications": {
          ".read": "auth != null && (root.child('servers').child($serverId).child('ownerUid').val() === auth.uid || root.child('clients').child(auth.uid).child('linked_servers').child($serverId).val() === true)",
          ".write": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",
          
          "$notificationId": {
            ".validate": "newData.hasChildren(['enc_pkg', 'enc_label', 'enc_title', 'enc_text', 'post_time'])",
            
            "enc_pkg": {
              ".validate": "newData.isString() && newData.val().length() > 0"
            },
            
            "enc_label": {
              ".validate": "newData.isString() && newData.val().length() > 0"
            },
            
            "enc_title": {
              ".validate": "newData.isString() && newData.val().length() > 0"
            },
            
            "enc_text": {
              ".validate": "newData.isString() && newData.val().length() > 0"
            },
            
            "post_time": {
              ".validate": "newData.isNumber() && newData.val() > 0"
            }
          }
        }
      }
    },
    
    "server_commands": {
      "$serverId": {
        ".read": "auth != null && root.child('servers').child($serverId).child('ownerUid').val() === auth.uid",
        ".write": "auth != null && root.child('clients').child(auth.uid).child('linked_servers').child($serverId).val() === true",
        
        "$commandId": {
          ".validate": "newData.hasChildren(['type', 'created_at']) && (newData.child('type').val() == 'request_location_update' || newData.child('type').val() == 'update_settings')",
          
          "type": {
            ".validate": "newData.val() == 'request_location_update' || newData.val() == 'update_settings'"
          },
          
          "created_at": {
            ".validate": "newData.isNumber() && newData.val() > 0"
          },
          
          "request_location_update": {
            ".validate": "newData.val() == true"
          },
          
          "update_settings": {
            ".validate": "newData.hasChildren(['active_interval', 'inactivity_timeout'])",
            
            "active_interval": {
              ".validate": "newData.isNumber() && newData.val() > 0 && newData.val() <= 3600000"  // максимум 1 час
            },
            
            "inactivity_timeout": {
              ".validate": "newData.isNumber() && newData.val() > 0 && newData.val() <= 86400000"  // максимум 24 часа
            }
          }
        }
      }
    },
    
    "clients": {
      "$clientId": {
        ".read": "auth != null && auth.uid === $clientId",
        ".write": "auth != null && auth.uid === $clientId",
        
        "linked_servers": {
          "$serverId": {
            ".validate": "newData.val() === true"
          }
        }
      }
    }
  }
}
```

## Как обновить правила

1. Зайдите в Firebase Console: https://console.firebase.google.com/
2. Выберите проект SafeOrbit
3. Перейдите в раздел "Realtime Database"
4. Нажмите на вкладку "Rules"
5. Замените текущие правила на новые безопасные правила выше
6. Нажмите "Publish" для публикации изменений

## Особенности новых правил

1. **Блокировка глобального доступа**: `.read: false` и `.write: false` в корне предотвращают глобальный доступ
2. **Доступ только к своим данным**: пользователи могут читать/писать только свои данные
3. **Ограниченный доступ к серверам**: только владелец и связанные клиенты могут получить доступ к серверу
4. **Защита pairing данных**: только владелец может читать/писать pairing информацию
5. **Однократное использование токена**: после установки consumed=true токен нельзя вернуть в исходное состояние
6. **Валидация команд**: проверка структуры и диапазонов значений команд
7. **Валидация уведомлений**: проверка структуры зашифрованных уведомлений

## Тестирование

После обновления правил протестируйте следующие сценарии:

1. Владелец сервера может регистрировать сервер и отправлять местоположение
2. Владелец может читать команды, отправленные клиентами
3. Клиент может использовать pairing token для связи с сервером
4. Клиент может отправлять команды владельцу сервера
5. Клиент может читать местоположение и уведомления сервера
6. Посторонние пользователи не могут получить доступ к чужим данным