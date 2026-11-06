# Источники новой версии

Материал ориентирован на Python 3.10+, Django 5.2, Pydantic 2, SQLAlchemy 2 и актуальные подходы FastAPI/Alembic. Ответы написаны как учебные объяснения. Не гарантируется применимость любого примера к произвольной версии фреймворка или схеме проекта.

- [Django QuerySet API](https://docs.djangoproject.com/en/5.2/ref/models/querysets/)
- [Django transactions и on_commit](https://docs.djangoproject.com/en/5.2/topics/db/transactions/)
- [Django async support](https://docs.djangoproject.com/en/5.2/topics/async/)
- [Django DB optimization](https://docs.djangoproject.com/en/5.2/topics/db/optimization/)
- [FastAPI async и threadpool](https://fastapi.tiangolo.com/async/)
- [FastAPI yield dependencies](https://fastapi.tiangolo.com/tutorial/dependencies/dependencies-with-yield/)
- [FastAPI response_model](https://fastapi.tiangolo.com/tutorial/response-model/)
- [FastAPI BackgroundTasks](https://fastapi.tiangolo.com/tutorial/background-tasks/)
- [Alembic tutorial](https://alembic.sqlalchemy.org/en/latest/tutorial.html)
- [Alembic autogenerate](https://alembic.sqlalchemy.org/en/latest/autogenerate.html)
- [Alembic branches](https://alembic.sqlalchemy.org/en/latest/branches.html)
- [Alembic operations](https://alembic.sqlalchemy.org/en/latest/ops.html)
- [SQLAlchemy async session](https://docs.sqlalchemy.org/en/20/orm/extensions/asyncio.html)
- [SQLite SELECT](https://www.sqlite.org/lang_select.html)
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android media playback](https://developer.android.com/media/implement/playback-app)

SQL-тренажёр использует SQLite, не PostgreSQL. Для ограничения расхода ресурсов разрешён учебный набор агрегатов и скалярных функций; генераторы больших строк, загрузка расширений, конкатенация, несколько запросов и запись отключены. Фрагменты Django/FastAPI/Alembic требуют импортов, моделей и окружения соответствующего проекта.
