## RuStore static backend

В этой папке оставлена только статическая схема:

- `catalog-data.json` — исходный каталог
- `apks/` — APK-файлы
- `media/` — иконки/скриншоты
- `build-static-site.mjs` — генератор статики
- `static-site/` — готовые файлы для загрузки на хостинг

### Сборка статики

```bash
cd backend
npm run build:static
```

После команды обновится папка `static-site/`.
На хостинг загружается содержимое `static-site/`.
