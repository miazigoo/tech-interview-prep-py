from android_smoke import *
assert SERIAL.startswith('emulator-')
def reach(s):
 for _ in range(14):
  if s in texts():return tap(s)
  adb('shell','input','swipe','540','1800','540','650','250')
 raise AssertionError(s)
adb('shell','am','force-stop','com.python.interviewcards');adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.5)
reach('Режимы и тренажёры  →');reach('План подготовки к интервью');reach('Начать дневное занятие');reach('Показать ответ и пример');reach('Далее без оценки');reach('Завершить занятие');reach('Выбрать другой режим');reach('План подготовки к интервью')
assert any('Завершено сегодня: 1' in t for t in texts());shot('v3_plan_progress')
print('PASS: daily plan counts completed cards',flush=True)
adb('shell','am','force-stop','com.python.interviewcards');adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.4)
reach('Режимы и тренажёры  →');reach('План подготовки к интервью');assert any('Завершено сегодня: 1' in t for t in texts())
print('PASS: plan survives process restart',flush=True)
