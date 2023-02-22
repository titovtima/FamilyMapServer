# FamilyMapServer

Сервер, чтобы отслеживать местоположение, сохранять историю передвижений и делиться местоположением и историей с другими пользователями

Взаимодействие с сервером происходит через HTTP-запросы

Сервер работает по адресу https://familymap.titovtima.ru

## Возможные запросы:

### Без авторизации:

#### POST /auth/registration
*Регистрация пользователя*

Тело запроса - данные для регистрации в формате JSON.
Необходимо указать логин, пароль и имя пользователя.

Например:
```
{
  "login": "test_user",
  "password": "Test_password",
  "name": "Новый пользователь"
}
```
Сервер возвращает код 201, если регистрация прошла успешно или код 400, если возникла ошибка (например, пользователь с таким логином уже существует).

### С авторизацией:

Используется базовая авторизация HTTP.
Необходимо указать в запросе HTTP-заголовок "Authorization" со значением "Basic \<credentials\>",
где credentials это логин и пароль, разделённые двоеточием и закодированные в Base64.

Например: `Authorization: Basic dGVzdF91c2VyOlRlc3RfcGFzc3dvcmQ=` для пользователя из примера для [регистрации](#post-authregistration)

Если авторизация не прошла успешно, возвращается код 401.

#### GET /auth/login
*Вход, получение информации о пользователе*

В теле ответа возвращаются данные пользователя в формате JSON.
Данные содержат логин, имя и список контактов пользователя.

Например:
```
{
  "login": "test_user",
  "name": "Новый пользователь",
  "contacts": [
    {
      "contactId": 1,
      "login": "test_user_2",
      "name": "Ещё один пользователь",
      "showLocation": true
    }
  ]
}
```

#### POST /auth/changePassword
*Смена пароля*

В теле запроса указывается новый пароль (в формате простого текста).
Пароль пользователя заменяется новым.
При следующем запросе в HTTP-заголовке "Authorization" необходимо указывать уже новый пароль.

#### POST /auth/changeName
*Смена имени*

В теле запроса указывается новое имя (в формате простого текста).
Имя пользователя в контактах других пользователей не меняется.

#### POST /location
*Отправка данных о местоположении*

В теле запроса указывается местоположение в формате JSON.  
У корневого объекта JSON указываются свойства:  
`latidude` - широта в градусах, умноженная на 1 000 000 и округлённая до целого числа  
`longitude` - долгота в градусах, умноженная на 1 000 000 и округлённая до целого числа  
`date` - дата и время, одно целое число, равное количеству миллисекунд с 1 января 1970 года, 00:00:00 GMT

Например:
```
{
  "latitude": 59939073,
  "longitude": 30315386,
  "date": 1677074199141
}
```
##### Хранение местоположения пользователя
Для каждого пользователя постоянно хранится последнее известное местоположение.
При получении POST-запроса на адрес "/location", если последнее известное местоположение не определено
(например, пользователь ещё ни разу не отправлял своё местоположение), 
или если дата полученного нового местоположения больше, чем дата записанного последнего известного,
местоположение пользователя обновляется.

Каждые 5 минут последнее известное местоположение пользователя автоматически записывается в историю местоположений,
если его дата не совпадает ни с каким уже записанным.

Каждый час все записи местоположений, которым больше 7 дней, удаляются из базы.

#### POST /shareLocation/share/{userLogin}
*Дать доступ к местоположению*

Предоставляет доступ к местоположению (пользователя, авторизованного с помощью HTTP-авторизации) 
пользователю с логином, указанным в URL вместо "{userLogin}"

Если пользователя с таким логином нет, или логин является логином авторизовавшегося пользователя, возвращается код 400.

#### POST /shareLocation/stop/{userLogin}
*Остановить доступ к местоположению*

Отнимает доступ к местоположению (пользователя, авторизованного с помощью HTTP-авторизации) 
у пользователя с логином, указанным в URL вместо "{userLogin}"

Если пользователя с таким логином нет, или логин является логином авторизовавшегося пользователя, возвращается код 400.

#### POST /shareLocation/ask/{userLogin}
*Попросить доступ к местоположению*

Запрашивает доступ (для пользователя, авторизованного с помощью HTTP-авторизации)
к местоположению пользователя с логином, указанным в URL вместо "{userLogin}"

Если пользователя с таким логином нет, или логин является логином авторизовавшегося пользователя, возвращается код 400.

Если доступ к местоположению пользователя уже имеется, возвращается код 239.
Иначе возвращается код 200 и запрос сохраняется в базе данных.

#### GET /shareLocation/getAsks

Возвращает список логинов пользователей, запросивших доступ к местоположению (пользователя, авторизованного с помощью HTTP-авторизации)
в формате простого текста, каждый логин на отдельной строке.

Например:
```
test_user2
VasyaPupkin
Ivan1989
```

#### GET /location/last/{userLogin}

Возвращает последнее известное местоположение пользователя с логином, записанным в URL вместо "{userLogin}".

Если пользователь с таким логином не найден, или его последнее известное местоположение не определено, возвращается код 404.

Если у авторизированного пользователя нет доступа к местоположению этого пользователя, возвращается код 403.

##### Формат возвращаемого местоположения:

Широта и долгота кодируются 4 байтами, дата кодируется 8 байтами.
Все они склеиваются в одну строку (16 байт) и затем кодируются в Base64.
Полученная строка в формате простого текста и является телом ответа.

Наример:
```
AZmSA3qTzgFlcml5hgEAAA==
```
(То же местоположение, что в пункте [отправки данных местоположения](#post-location))


#### GET /location/history/{userLogin}

Возвращает историю местоположения пользователя с логином, записанным в URL вместо "{userLogin}".

Если пользователь с таким логином не найден, или его история местоположения пустая, возвращается код 404.

Если у авторизированного пользователя нет доступа к местоположению этого пользователя, возвращается код 403.

##### Формат возвращаемого местоположения:

Широта и долгота кодируются 4 байтами, дата кодируется 8 байтами.
Данные всех местоположений из истории склеиваются в одну строку (16 байт * количество местололожений) и затем кодируются в Base64.
Полученная строка в формате простого текста и является телом ответа.

#### POST /contacts/add
*Добавление контакта*

В теле запроса указываются данные нового контакта в формате JSON.  
У корневого объекта JSON указываются свойства:  
`login` - логин пользователя, добавляемого в контакты  
`name` (необязательно) - имя контакта, по умолчанию устанавливается имя пользователя  
`showLocation` (boolean, необязательно) - отображать ли местоположение пользователя, по умолчанию `true`

Например:
```
{
  "login": "test_user2",
  "name": "Второй пользователь",
  "showLocation": true
}
```
```
{
  "login": "test_user3"
}
```
```
{
  "login": "VasyaPupkin",
  "name": "Вася из Саратова"
}
```
```
{
  "login": "Ivan1989",
  "showLocation": false
}
```

При ошибке добавления контакта возвращается код 400.
Например, если логин не указан, или пользователя с таким логином не существует, или если контакт с таким логином уже добавлен
(в последнем случае можно обновить контакт с помощью [update](#post-contactsupdate))

При успешном добавлении контакта возвращается id нового контакта - одно целое число в формате простого текста.

* Имя контакта не обновляется, если пользователь меняет своё имя
* Если пользователь не дал доступ к местоположению, `showLocation=true` всё равно не позволит получить его местоположение.
  Это дополнительный параметр, работающий только при полученном доступе.
* При удалении пользователя, на которого ссылается контакт, контакт автоматически не удаляется.
  Но логин пользователя становится равен `null`.

#### POST /contacts/update
*Обновление контакта*

В теле запроса указываются данные для изменения контакта в формате JSON.  
У корневого объекта JSON указываются свойства:  
`contactId` - id изменяемого контакта  
`name`(необязательно) - новое имя контакта  
`showLocation`(необязательно) - новое значение showLocation для контакта

Например:
```
{
  "contactId": 1,
  "name": "Новый пользователь 2",
  "showLocation": false
}
```
```
{
  "contactId": 2,
  "showLocation": true
}
```
```
{
  "contactId": 3,
  "name": "Василий Пупкин"
}
```
```
{
  "contactId": 4
}
// Контакт не поменяется, запрос бессмысленный
```

Для контакта с id равным `contactId` устанавливаются переданные значения `name` и `showLocation`.
Если какое-то из значений не было передано, оно не меняется.

При ошибке изменения контакта возвращается код 400.
Например, если `contactId` не указан, или такого контакта не существует.

* `contactId` можно получить при добавлении контакта, либо при получении информации пользователя через [/auth/login](#get-authlogin)

#### POST /contacts/delete
*Удаление контакта*

В теле запроса указываются данные для удаления контакта в формате JSON.  
У корневого объекта JSON указываются свойство `contactId` - id удаляемого контакта.  
Остальные свойства, при наличии, будут проигнорированы.

Например:
```
{
  "contactId": 2
}
```

Удаляется контакт с id равным `contactId`. Возвращается код 200, даже если контакт не существовал.


