"""Check a 320dp viewport with 140% system font; restore emulator settings."""
from android_smoke import *
assert SERIAL.startswith('emulator-')
def reach(s):
    for _ in range(18):
        if s in texts():return tap(s)
        adb('shell','input','swipe','360','950','360','430','400')
    raise AssertionError(s)
try:
    adb('shell','wm','size','720x1280');adb('shell','wm','density','360');adb('shell','settings','put','system','font_scale','1.4')
    adb('shell','am','force-stop','com.python.interviewcards');adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.8)
    reach('Продолжить обучение  →');shot('v4_small_card')
    reach('Показать ответ и пример');shot('v4_small_answer')
    reach('▷ Озвучить ответ');time.sleep(.5)
    reach('Фраза');shot('v4_small_player')
    assert any(n.get('content-desc')=='Следующая карточка' for n in nodes().iter('node'))
    reach('✓ Запомнил')
    print('PASS: 320dp width / 140% font; answer, scrollable player, phrase and status controls reachable')
finally:
    adb('shell','settings','put','system','font_scale','1.0');adb('shell','wm','size','reset');adb('shell','wm','density','reset')
