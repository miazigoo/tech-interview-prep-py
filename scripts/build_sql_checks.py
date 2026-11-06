import json,sqlite3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'data/exercises.json';data=json.loads(p.read_text())
create=data['sql_schema'].split('INSERT INTO')[0]
fixtures=[create,
create+'''INSERT INTO users VALUES(9,'Нина','Москва'),(5,'Олег','Омск'),(7,'Нина','Москва'),(2,'Павел',NULL),(12,'Юлия','Москва');
INSERT INTO orders VALUES(1,9,1000,'paid'),(2,9,1000,'paid'),(3,5,9000,'cancelled'),(4,7,1000,'paid'),(5,2,0,'new');
INSERT INTO order_items VALUES(1,1,'Книга',1),(2,1,'Книга',2),(3,4,'Лампа',4);''',
create+'''INSERT INTO users VALUES(1,'Ира',NULL),(2,'Лев','Москва'),(3,'Ира','Казань');
INSERT INTO orders VALUES(6,1,50,'new'),(8,2,200,'paid'),(9,2,200,'new'),(10,3,1500,'paid');
INSERT INTO order_items VALUES(1,8,'Книга',5),(2,10,'Ручка',1),(3,10,'Книга',5);''']
for t in data['sql']:
    con=sqlite3.connect(':memory:');con.executescript(data['sql_schema']);t['column_count']=len(con.execute(t['solution']).description);con.close()
    t['checks']=[]
    for schema in fixtures:
        con=sqlite3.connect(':memory:');con.executescript(schema);rows=[list(r) for r in con.execute(t['solution'])];con.close()
        t['checks'].append(dict(expected=rows,ordered=t['ordered'],column_count=t['column_count']))
data['sql_check_schemas']=fixtures
for dest in [p,ROOT/'android/app/src/main/assets/exercises.json']:dest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
print('Validated 15 SQL solutions on 4 datasets each')
