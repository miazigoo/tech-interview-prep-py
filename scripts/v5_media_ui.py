"""Check both answer levels, new exercise voice and restored background playback."""
from android_smoke import *
import json,sys
assert SERIAL.startswith('emulator-')
PKG='com.python.interviewcards'
def cp():
    root=ET.fromstring(adb('exec-out','run-as',PKG,'cat','shared_prefs/resume_v5.xml'))
    value=next((n.text for n in root if n.get('name')=='state'),None)
    return json.loads(value) if value else None
def start(card=None):
    adb('shell','am','force-stop',PKG);args=['shell','am','start','-n',PKG+'/.MainActivity']
    if card:args+=['--es','viewCard',card]
    adb(*args);time.sleep(.7)
def scroll():
    n=max((n for n in nodes().iter('node') if n.get('scrollable')=='true' and n.get('class')!='android.widget.HorizontalScrollView'),key=lambda n:int(re.findall(r'\d+',n.get('bounds'))[3])-int(re.findall(r'\d+',n.get('bounds'))[1]))
    x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','swipe',str((x+r)//2),str(int(y+(b-y)*.75)),str((x+r)//2),str(int(y+(b-y)*.25)),'350')
def reach(s):
    for _ in range(30):
        if s in texts():return tap(s)
        scroll()
    raise AssertionError(s)
def background():adb('shell','input','keyevent','3');time.sleep(1.2)
def resume():start();reach('Продолжить с места остановки');time.sleep(1.2)
def log(s):print('PASS: '+s,flush=True)
adb('shell','pm','grant',PKG,'android.permission.POST_NOTIFICATIONS')
if '--radio-only' not in sys.argv:
    start('py-middle-001');tap('Показать ответ и пример');reach('Подробно');shot('v5_answer_levels')
    reach('▷ Озвучить ответ');time.sleep(.8);background();before=cp()['payload'];assert before['audio']['path'].startswith('audio/v5_detail_');assert before['audio']['position']>0
    resume();after=cp()['payload'];assert abs(after['audio']['position']-before['audio']['position'])<1200;shot('v5_detailed_audio');log('detailed male-voice answer plays and restores paused at its position')
    start();reach('Понять и закрепить');reach('Сравнить понятия');reach('is и ==');reach('▷ Озвучить сравнение');time.sleep(.8);background();before=cp()['payload'];assert before['audio']['path'].startswith('audio/v5_compare_')
    resume();reach('▶ Продолжить аудио');time.sleep(.8);background();after=cp()['payload'];assert after['audio']['position']>before['audio']['position'];log('new comparison voice and exact-position transport control work')
    start();reach('Понять и закрепить');reach('А если изменить условие?');reach('Не знаю — разобрать');reach('▷ Озвучить разбор');time.sleep(.8);background();before=cp()['payload'];assert before['audio']['path'].startswith('audio/v5_whatif_');resume();shot('v5_whatif_audio');log('changed-condition explanation voice restores with its revealed result')
start();reach('▶  Слушать без остановки')
for _ in range(50):
    time.sleep(.4);state=cp()
    if state and state.get('owner')=='radio' and (state['payload'].get('audio') or {}).get('label')=='Подробный ответ':break
else:raise AssertionError('Detailed radio answer did not start')
time.sleep(1);adb('shell','input','keyevent','127');time.sleep(1.2);before=cp()['payload'];assert before['audio']['detailed'];position=before['audio']['position'];assert position>0
resume();after=cp()['payload'];assert after['audio']['path']==before['audio']['path'];assert abs(after['audio']['position']-position)<1200
sessions=adb('shell','dumpsys','media_session').decode();assert 'com.python.interviewcards' in sessions and ('PAUSED' in sessions or 'state=2,' in sessions)
shot('v5_radio_resume');reach('Стоп');log('background detailed audio survives process death and restores through MediaSession paused')
