from android_smoke import *
assert SERIAL.startswith('emulator-')
def reach(s):
    for _ in range(14):
        if s in texts():return tap(s)
        adb('shell','input','swipe','540','1800','540','650','250')
    raise AssertionError(s)
adb('shell','pm','clear','com.python.interviewcards');adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.5)
reach('Режимы и тренажёры  →');reach('План подготовки к интервью');assert any('Дневной минимум' in t for t in texts());shot('v3_plan');adb('shell','input','keyevent','4')
reach('Решать Python-задачи');reach('Проверить предположение');reach('Нужно повторить');adb('shell','input','keyevent','4');reach('Мои ошибки · 1');assert 'Нужно' not in texts();shot('v3_mistakes')
reach('Попробовать ещё раз');assert any('[4]' in t for t in texts());reach('Проверить предположение');reach('Я ответил верно');assert any('Здесь пока пусто' in t for t in texts());print('PASS: code mistake, new example and explicit successful retry',flush=True)
adb('shell','input','keyevent','4');reach('Практические кейсы Middle');assert any('API списка заказов' in t for t in texts());reach('Сравнить с разбором');shot('v3_case');reach('Нужно разобрать ещё раз');adb('shell','input','keyevent','4')
reach('Мои ошибки · 1');assert any('API списка заказов' in t for t in texts());print('PASS: cases and saved weak case',flush=True)
adb('shell','input','keyevent','4');reach('Тренироваться в SQL');reach('Подсказка');reach('Показать решение');reach('Выполнить и проверить');time.sleep(.3);assert any('Верно с подсказкой' in t for t in texts());shot('v3_sql')
adb('shell','input','keyevent','4');reach('Мои ошибки · 2');print('PASS: successful assisted SQL remains in mistakes',flush=True)
