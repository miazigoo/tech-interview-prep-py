"""320dp / 140% font check for version 5."""
from android_smoke import *
assert SERIAL.startswith('emulator-')
def reach(s):
    for _ in range(25):
        if s in texts():return tap(s)
        adb('shell','input','swipe','360','950','360','430','400')
    raise AssertionError(s)
try:
    adb('shell','pm','clear','com.python.interviewcards')
    adb('shell','wm','size','720x1280');adb('shell','wm','density','360');adb('shell','settings','put','system','font_scale','1.4')
    adb('shell','am','force-stop','com.python.interviewcards');adb('shell','am','start','-n','com.python.interviewcards/.MainActivity','--es','viewCard','py-middle-001');time.sleep(.8)
    for _ in range(15):
        if 'Показать ответ и пример' in texts():break
        time.sleep(.3)
    else:raise AssertionError('Study screen did not load')
    shot('v5_small_question');reach('Показать ответ и пример');reach('Коротко');shot('v5_small_answer');reach('Чек-лист хорошего ответа');shot('v5_small_checklist')
    reach('Самопроверка ответа');reach('✓ Запомнил')
    print('PASS: 320dp / 140% font; compact question, answer levels, checklist, scroll and status controls accessible')
finally:
    adb('shell','settings','put','system','font_scale','1.0');adb('shell','wm','size','1080x2400');adb('shell','wm','density','440')
