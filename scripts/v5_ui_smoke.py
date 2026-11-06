"""End-to-end process-death checks on a disposable emulator, never on a phone."""
from android_smoke import *
import json,sys
assert SERIAL.startswith('emulator-')
PKG='com.python.interviewcards'
def checkpoint():
    raw=adb('exec-out','run-as',PKG,'cat','shared_prefs/resume_v5.xml')
    tree=ET.fromstring(raw)
    return json.loads(next(n.text for n in tree if n.get('name')=='state'))
def start(card=None):
    adb('shell','am','force-stop',PKG)
    args=['shell','am','start','-n',PKG+'/.MainActivity']
    if card:args+=['--es','viewCard',card]
    adb(*args);time.sleep(.7)
def scroll(up=False):
    ns=[n for n in nodes().iter('node') if n.get('scrollable')=='true' and n.get('class')!='android.widget.HorizontalScrollView']
    assert ns,'No scroll surface'
    n=max(ns,key=lambda n:int(re.findall(r'\d+',n.get('bounds'))[3])-int(re.findall(r'\d+',n.get('bounds'))[1]))
    x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')));lo=int(y+(b-y)*.25);hi=int(y+(b-y)*.75)
    adb('shell','input','swipe',str((x+r)//2),str(lo if up else hi),str((x+r)//2),str(hi if up else lo),'350');time.sleep(.2)
def reach(s,up=False):
    for _ in range(35):
        if s in texts():return tap(s)
        scroll(up)
    raise AssertionError(s)
def back():adb('shell','input','keyevent','4');time.sleep(.3)
def input_text(value):
    for n in nodes().iter('node'):
        if n.get('class')=='android.widget.EditText':
            x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x+r)//2),str((y+b)//2));adb('shell','input','text',value);back();return
    raise AssertionError('No input field')
def resume():start();reach('Продолжить с места остановки');time.sleep(.5)
def log(s):print('PASS: '+s,flush=True)
# Checklist: a saved independent grade is scheduled, and a separate ungraded learned card is excluded.
if '--resume-tests' not in sys.argv:
    adb('shell','pm','clear',PKG);start('py-middle-001');tap('Показать ответ и пример');reach('Чек-лист хорошего ответа')
    content=json.loads((Path(__file__).resolve().parents[1]/'data/learning_v5.json').read_text())
    for point in content['cards']['py-middle-001']['checklist']:reach(point)
    reach('Сохранить самооценку по чек-листу');shot('v5_checklist');log('checklist saved')
    start('py-middle-002');reach('✓ Запомнил');start();reach('Повторить сегодня · 153');shot('v5_due')
    reach('Включить запомненные в добровольную проверку');tap('Включить');assert any('Готово к повторению: 154' in s for s in texts());log('learned opt-in changes only scheduled-review coverage')
# Exact audio/card restoration. The MainActivity intent is an existing public navigation entry point.
start('py-middle-001');tap('Показать ответ и пример')
for _ in range(35):
    if 'Фраза' in texts():break
    scroll()
tree=nodes()
def center(n):
    x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')));return str((x+r)//2),str((y+b)//2)
play_node=next(n for n in tree.iter('node') if n.get('text','').startswith('▶'))
seek_node=next(n for n in tree.iter('node') if n.get('content-desc')=='Позиция аудиотрека')
play_xy=center(play_node);seek_xy=center(seek_node)
adb('shell','input','tap',*play_xy)
for _ in range(30):
    time.sleep(.5)
    if (checkpoint()['payload'].get('audio') or {}).get('label')=='Ответ':break
adb('shell','input','tap',*seek_xy);time.sleep(.4)
adb('shell','input','tap',*play_xy);time.sleep(1.2)
before=checkpoint()['payload'];position=before['audio']['position'];assert position>2000
resume();after=checkpoint()['payload'];assert after['queue']==before['queue'];assert abs(after['audio']['position']-position)<1200
assert any(t.startswith('▶') and 'Продолжить' in t for t in texts());shot('v5_restored_audio');log('paused audio position, card and queue survive force-stop')
# Finite lesson: exact question order/index and paused active time.
start();reach('Режимы и тренажёры  →');reach('20 карточек');time.sleep(1.2);before=checkpoint()['payload']
resume();time.sleep(1.2);after=checkpoint()['payload'];assert after['session']['ids']==before['session']['ids'];assert after['session']['index']==before['session']['index'];assert after['session']['active']<=before['session']['active']+1200
shot('v5_restored_lesson');log('unfinished finite session restores exact order and excludes closed-app time')
# SQL draft and hint stage survive, without revealing further hints.
start();reach('Режимы и тренажёры  →');reach('Тренироваться в SQL');reach('Выполнить и проверить')
# Blank execution is safe; enter a draft before using a hint.
for _ in range(8):
    if any(n.get('class')=='android.widget.EditText' for n in nodes().iter('node')):break
    scroll(True)
input_text('SELECT%sname%sFROM%susers%sORDER%sBY%sid');reach('Подсказка 1 — намёк');time.sleep(1)
before=checkpoint()['payload'];assert before['hints']['SQL:0:false']==1
resume();after=checkpoint()['payload'];assert after['drafts']==before['drafts'];assert after['hints']==before['hints'];shot('v5_restored_sql');log('SQL draft and hint stage survive force-stop')
# Changed conditions: own prediction, hidden result, saved draft and open state.
start();reach('Понять и закрепить');reach('А если изменить условие?');input_text('The%soriginal%slist%sstays%sunchanged');reach('Проверить прогноз');time.sleep(1)
before=checkpoint()['payload'];assert before['opened'];resume();after=checkpoint()['payload'];assert after['opened'];assert after['draft']==before['draft'];shot('v5_whatif');log('changed-condition prediction and revealed explanation restored')
# A comparison is a separate persisted exercise.
back();reach('Сравнить понятия');reach('is и ==');reach('Сверить выбор')
for _ in range(8):
    if any(n.get('class')=='android.widget.EditText' for n in nodes().iter('node')):break
    scroll(True)
input_text('Use%sequality');reach('Сверить выбор');time.sleep(1);before=checkpoint()['payload'];resume();after=checkpoint()['payload'];assert after['mode']=='COMPARE';assert after['draft']==before['draft'];assert after['opened'];shot('v5_comparison');log('comparison choice and explanation survive force-stop')
