"""Exercise the version 4 flows on a disposable emulator, using real visible controls."""
from android_smoke import *
assert SERIAL.startswith('emulator-')
PKG='com.python.interviewcards'
def scroll(up=False):
    tree=nodes()
    ns=[n for n in tree.iter('node') if n.get('scrollable')=='true' and n.get('class')=='android.widget.ScrollView']
    if not ns:ns=[n for n in tree.iter('node') if n.get('scrollable')=='true']
    assert ns,'No scroll surface'
    n=max(ns,key=lambda n:int(re.findall(r'\d+',n.get('bounds'))[3])-int(re.findall(r'\d+',n.get('bounds'))[1]))
    x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')))
    lo=int(y+(b-y)*.3);hi=int(y+(b-y)*.7)
    adb('shell','input','swipe',str((x+r)//2),str(lo if up else hi),str((x+r)//2),str(hi if up else lo),'400')
    time.sleep(.2)
def reach(s,up=False):
    for _ in range(35):
        if s in texts():return tap(s)
        scroll(up)
    raise AssertionError('Unreachable: '+s)
def back():adb('shell','input','keyevent','4');time.sleep(.3)
def start():adb('shell','am','force-stop',PKG);adb('shell','am','start','-n',PKG+'/.MainActivity');time.sleep(.7)
adb('shell','pm','clear',PKG);start()
tap('Начать обучение  →')
assert '×1' not in texts()
shot('v4_card_compact')
tap('Действия и заметки');reach('Моя заметка')
for n in nodes().iter('node'):
    if n.get('class')=='android.widget.EditText':
        x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x+r)//2),str((y+b)//2));break
adb('shell','input','text','V4_note_background');adb('shell','input','keyevent','3');time.sleep(.7)
start();tap('Продолжить обучение  →');tap('Действия и заметки');reach('Моя заметка')
assert 'V4_note_background' in texts();shot('v4_note');start()
reach('Режимы и тренажёры  →');reach('Готовность по темам')
assert 'Пока нет самопроверок' in texts();shot('v4_readiness_empty');back()
reach('Учебные цепочки');reach('Открыть цепочку')
assert 'Шаг 1 из 6' in texts();assert any('is' in s and '==' in s for s in texts())
reach('Показать ответ и связь с примером');shot('v4_chain')
reach('Самостоятельно');reach('Следующий шаг');assert 'Шаг 2 из 6' in texts()
back();back();start();reach('Режимы и тренажёры  →');reach('Учебные цепочки');reach('Продолжить цепочку');assert 'Шаг 2 из 6' in texts()
back();reach('Готовность по темам');assert 'Самооценка ответов: 100%' in texts();shot('v4_readiness');back()
reach('Решать Python-задачи');assert 'Результат' not in texts();reach('Подсказка 1 — намёк');assert 'Результат' not in texts()
reach('Подсказка 2 — подход');assert 'Результат' not in texts();shot('v4_hints');reach('Показать решение');reach('Я ответил верно');back()
reach('Мои ошибки · 1');assert any('Python' in s or 'код' in s for s in texts());back()
reach('Тренироваться в SQL');reach('Подсказка 1 — намёк');assert 'Показать решение' not in texts();reach('Подсказка 2 — подход');reach('Показать решение');reach('Выполнить и проверить');time.sleep(.5)
assert any('Верно с подсказкой' in s for s in texts());shot('v4_sql');back()
reach('Практические кейсы Middle');reach('Подсказка 1 — намёк');reach('Подсказка 2 — подход');reach('Показать решение');shot('v4_case');back()
reach('Начать собеседование');tap('90 секунд');reach('Открыть ответ досрочно');reach('Открыть ориентир 1');reach('С подсказкой');shot('v4_followup')
back();back();reach('Готовность по темам');shot('v4_readiness_after_interview')
print('PASS: compact controls; note saved on background/restart; empty readiness; ordered chain resumes; separate self-assessment; Python/SQL/case hint stages; assisted success remains mistake; interview follow-up',flush=True)
