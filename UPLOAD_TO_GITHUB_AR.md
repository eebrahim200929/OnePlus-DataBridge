# رفع المشروع إلى GitHub

1. فك ضغط ZIP أولاً. GitHub لا يفك ZIP تلقائياً عند رفعه.
2. ارفع محتويات المشروع إلى جذر المستودع، وليس داخل مجلد إضافي.
3. تأكد من وجود `.github/workflows/build-apk.yml`.
4. افتح Actions ثم شغّل Build debug APK، أو انتظر تشغيله بعد push.
5. بعد نجاح البناء، حمّل OnePlusDataBridge-Debug-APK من Artifacts.

المشروع غير مختبر على جهاز OnePlus 12، ويحتاج Shizuku ولا يلغي رسائل النظام في التطبيقات الأخرى.
