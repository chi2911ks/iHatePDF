# Offline document engine

Module này vendor source các project không thuộc Google cần cho luồng PDF/Word offline.

- `upstream/pdfbox-android`: PdfBox-Android tag `v2.0.27.0`, được compile trực tiếp trong `:offline-document-engine`.
- `upstream/apache-poi`: Apache POI tag `REL_5_5_1`, được compile bởi module `:poi-source-engine`.
- `upstream/jp2-android`: JP2ForAndroid commit `dfb82e647d35eadb8ad50670682bf6493ec74351`; Java và C++ OpenJPEG được compile trong `:offline-document-engine`.
- `libs/`: chỉ giữ binary của dependency bắc cầu và `poi-ooxml-lite` generated schemas.

## Android patches

POI có các tiện ích PowerPoint tùy chọn phụ thuộc Batik/FOP/XML Security dành cho desktop. Source build Android loại các package tùy chọn đó và giữ nguyên phần `poi`, `poi-ooxml`, `XWPF`, `HWPF` cần cho DOC/DOCX. API tạo preview SVG trong PowerPoint báo `UnsupportedOperationException`; tính năng này không thuộc luồng PDF/Word.

`poi-ooxml-lite` vẫn là JAR vì đây là mã schema được sinh trong quy trình release POI và không có source Java tương ứng trong Git repository.

Không bundle ML Kit, Google Play Services, Firebase, Kotlin hoặc coroutines. AndroidX annotation chỉ là compile-time dependency cho source JP2.

## Build offline

```powershell
.\gradlew.bat :pdf-word-converter:testDebugUnitTest :app:assembleDebug --offline
```

Để refresh đúng các dependency bắc cầu đã pin trong version catalog:

```powershell
.\gradlew.bat :offline-document-engine:syncVendoredLibraries
```

Sau khi refresh cần chạy build/test và audit `THIRD_PARTY_LIBRARIES.md` cùng LICENSE/NOTICE của từng project.
