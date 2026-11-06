import json,subprocess,sys,sqlite3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
code_rows=r'''
1|a = [1, 2]\nb = a\nb.append(3)\nprint(a)|Оба имени указывают на один список: append меняет общий объект.
2|a = [1, 2]\nb = a[:]\nb.append(3)\nprint(a, b)|Срез создаёт новый внешний список; здесь элементы — числа, поэтому изменение b не затрагивает a.
3|def f(items=[]):\n    items.append(1)\n    return len(items)\nprint(f(), f())|Один список по умолчанию переиспользуется между вызовами.
4|g = (x*x for x in range(3))\nprint(list(g))\nprint(list(g))|Генератор исчерпывается первым проходом.
5|funcs = [lambda: i for i in range(3)]\nprint([f() for f in funcs])|Замыкания читают одно имя i после завершения цикла.
6|funcs = [lambda i=i: i for i in range(3)]\nprint([f() for f in funcs])|Значение аргумента по умолчанию фиксируется на каждой итерации.
7|print(bool("False"), bool([]), bool([0]))|Истинность строки зависит от её пустоты, а не от написанного слова.
8|a = [3, 1, 2]\nb = a.sort()\nprint(a, b)|sort меняет список на месте и возвращает None.
9|d = {"b": 1, "a": 2}\nd["b"] = 3\nprint(list(d))|Обновление значения существующего ключа не меняет порядок вставки.
10|matrix = [[0]*2]*2\nmatrix[0][0] = 9\nprint(matrix)|Обе строки матрицы — ссылки на один вложенный список.
11|a = [1, 2]\nb = [1, 2]\nprint(a == b, a is b)|Равные значения могут принадлежать разным объектам.
12|def f():\n    try:\n        return 1\n    finally:\n        return 2\nprint(f())|return из finally заменяет подготовленный результат. Так можно случайно скрыть и исключение.
13|print(list(zip([1, 2, 3], ["a"])))|Обычный zip останавливается при исчерпании самого короткого входа.
14|print(any([]), all([]))|Для пустого набора any ложен, all истинен: не найдено ни одного контрпримера.
15|from collections import Counter\nprint(Counter("ababa")["a"])|Counter считает частоты элементов, здесь буква a встречается три раза.
16|a = [[1]]\nb = a.copy()\nb[0].append(2)\nprint(a)|Поверхностная копия разделяет вложенный список с оригиналом.
'''
codes=[]
for line in code_rows.strip().splitlines():
 n,code,note=line.split('|');code=code.replace('\\n','\n')
 result=subprocess.run([sys.executable,'-I','-c',code],capture_output=True,text=True,check=True,timeout=3)
 codes.append(dict(id=f'code-{int(n):03}',title=f'Что выведет код {n}',code=code,answer=result.stdout.rstrip('\n'),explanation=note))
schema='''CREATE TABLE users(id INTEGER PRIMARY KEY, name TEXT, city TEXT);
CREATE TABLE orders(id INTEGER PRIMARY KEY, user_id INTEGER, amount INTEGER, status TEXT);
CREATE TABLE order_items(id INTEGER PRIMARY KEY, order_id INTEGER, product TEXT, qty INTEGER);
INSERT INTO users VALUES(1,'Анна','Москва'),(2,'Борис','Казань'),(3,'Вера','Москва'),(4,'Глеб',NULL);
INSERT INTO orders VALUES(101,1,1200,'paid'),(102,1,800,'new'),(103,2,500,'paid'),(104,3,2000,'paid'),(105,2,300,'cancelled');
INSERT INTO order_items VALUES(1,101,'Книга',2),(2,101,'Ручка',3),(3,103,'Книга',1),(4,104,'Рюкзак',1);'''
sql_rows=r'''
1|Получите имена всех пользователей по id по возрастанию.|SELECT name FROM users ORDER BY id|Порядок результата задаёт ORDER BY; сам порядок хранения строк не является контрактом.|Начните с SELECT name FROM users.|ordered
2|Получите имя и город пользователей из Москвы, по id.|SELECT name, city FROM users WHERE city = 'Москва' ORDER BY id|WHERE отбирает строки до формирования результата.|Сравните city с текстовым значением.|ordered
3|Получите id оплаченных заказов с суммой не меньше 1000, по id.|SELECT id FROM orders WHERE status = 'paid' AND amount >= 1000 ORDER BY id|AND требует выполнения обоих условий.|В WHERE нужны два условия.|ordered
4|Получите два самых дорогих заказа: id и amount. При равенстве суммы сортируйте по id.|SELECT id, amount FROM orders ORDER BY amount DESC, id LIMIT 2|DESC ставит большие суммы раньше, LIMIT ограничивает размер результата.|Нужны ORDER BY и LIMIT.|ordered
5|Посчитайте общее количество заказов. Одна колонка, одна строка.|SELECT COUNT(*) FROM orders|COUNT(*) считает строки, включая строки с NULL в отдельных колонках.|Используйте агрегат COUNT.|unordered
6|Для каждого user_id верните сумму оплаченных заказов; user_id по возрастанию.|SELECT user_id, SUM(amount) FROM orders WHERE status = 'paid' GROUP BY user_id ORDER BY user_id|Сначала фильтрация, затем группировка и SUM внутри каждой группы.|GROUP BY user_id после WHERE.|ordered
7|Верните id заказа, имя пользователя и сумму для paid-заказов, по id заказа.|SELECT o.id, u.name, o.amount FROM orders o JOIN users u ON u.id = o.user_id WHERE o.status = 'paid' ORDER BY o.id|JOIN сопоставляет заказ и владельца, а не склеивает строки по позиции.|Соедините orders.user_id с users.id.|ordered
8|Верните имена пользователей без заказов, по id пользователя.|SELECT u.name FROM users u LEFT JOIN orders o ON o.user_id = u.id WHERE o.id IS NULL ORDER BY u.id|LEFT JOIN оставляет пользователя без пары, после чего IS NULL выбирает его.|LEFT JOIN и проверка ключа правой таблицы.|ordered
9|Верните user_id и число заказов для пользователей с двумя и более заказами, по user_id.|SELECT user_id, COUNT(*) FROM orders GROUP BY user_id HAVING COUNT(*) >= 2 ORDER BY user_id|HAVING фильтрует уже сформированные группы.|Условие по COUNT пишется в HAVING.|ordered
10|Верните имя пользователя с неизвестным городом.|SELECT name FROM users WHERE city IS NULL|NULL проверяют через IS NULL, а не через равенство.|Не используйте city = NULL.|unordered
11|Верните id и amount заказов дороже среднего по всем заказам, по id.|SELECT id, amount FROM orders WHERE amount > (SELECT AVG(amount) FROM orders) ORDER BY id|Подзапрос вычисляет одно среднее значение для сравнения.|AVG в скалярном подзапросе.|ordered
12|Верните все разные статусы заказов по алфавиту.|SELECT DISTINCT status FROM orders ORDER BY status|DISTINCT удаляет повторяющиеся строки результата.|Выберите одну колонку с DISTINCT.|ordered
13|Для каждого пользователя верните имя и число заказов, включая ноль, по id пользователя.|SELECT u.name, COUNT(o.id) FROM users u LEFT JOIN orders o ON o.user_id = u.id GROUP BY u.id, u.name ORDER BY u.id|COUNT(o.id) игнорирует NULL в отсутствующей паре; COUNT(*) ошибочно дал бы один.|Нужны LEFT JOIN и COUNT ключа заказа.|ordered
14|Через CTE paid выберите user_id и сумму paid-заказов не меньше 1000, по user_id.|WITH paid AS (SELECT user_id, SUM(amount) AS total FROM orders WHERE status = 'paid' GROUP BY user_id) SELECT user_id, total FROM paid WHERE total >= 1000 ORDER BY user_id|CTE даёт имя промежуточному результату и делает этапы запроса явными.|WITH paid AS (...) SELECT ... FROM paid.|ordered
15|Верните название товара и суммарное количество по всем order_items, по названию.|SELECT product, SUM(qty) FROM order_items GROUP BY product ORDER BY product|Группировка объединяет одинаковые товары, SUM суммирует количество.|GROUP BY product.|ordered
'''
con=sqlite3.connect(':memory:');con.executescript(schema);tasks=[]
for line in sql_rows.strip().splitlines():
 n,title,solution,note,hint,order=line.split('|')
 expected=[list(r) for r in con.execute(solution)]
 tasks.append(dict(id=f'sql-{int(n):03}',title=title,solution=solution,explanation=note,hint=hint,ordered=order=='ordered',expected=expected))
result=dict(code=codes,sql_schema=schema,sql=tasks)
(ROOT/'data/exercises.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
(ROOT/'android/app/src/main/assets/exercises.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print('Executed',len(codes),'Python cases and',len(tasks),'SQL reference queries')
