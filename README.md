# iHatePDF Android library

Offline Android library chuyển đổi PDF ↔ Word, không gọi server và không tạo watermark.

## Chạy sample

```powershell
.\gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Dùng trong project này

```kotlin
val converter = AndroidDocumentConverter(context)

val editable = converter.pdfToDocx(
    inputPdfUri,
    outputDocxUri,
    PdfToDocxOptions(
        mode = PdfToDocxMode.EDITABLE,
        preservePageGraphics = true, // lớp trong suốt chỉ chứa image/vector/paint, không chứa text
    ),
)

val visual = converter.pdfToDocx(
    inputPdfUri,
    outputDocxUri,
    PdfToDocxOptions(mode = PdfToDocxMode.VISUAL),
)

val pdf = converter.wordToPdf(inputWordUri, outputPdfUri)
```

Gọi API từ coroutine/worker ngoài main thread. `EDITABLE` dùng PDFBox để lấy text/font và vẽ image/vector/path/paint vào một PNG trong suốt không chứa text, PDFium/PdfRenderer để phân loại-render, ML Kit cho trang scan và Apache POI để tạo rồi verify DOCX. Graphics layer được ghi bằng DrawingML floating anchor tại gốc trang; mặc định không chụp toàn bộ trang và không lặp chữ. Đặt `preservePageGraphics = false` nếu chỉ cần text/table và muốn DOCX nhẹ hơn. `VISUAL` giữ hình trang tốt hơn nhưng trang là ảnh. Input Word hỗ trợ `.docx` và `.doc`.

## Yêu cầu

- Android minSdk 26, compileSdk 37.
- Storage Access Framework `Uri` đọc/ghi được.
- Tối đa mặc định 300 trang; Word input tối đa 100 MB.
- OCR ML Kit được bundle trong APK và chạy offline.

## Kiểm thử

```powershell
.\gradlew.bat :pdf-word-converter:testDebugUnitTest :pdf-word-converter:lintDebug
.\gradlew.bat :pdf-word-converter:connectedDebugAndroidTest
```

Xem phạm vi, fidelity và roadmap production tại [docs/PDF_WORD_ANDROID_LIBRARY_PLAN.md](docs/PDF_WORD_ANDROID_LIBRARY_PLAN.md). Nghĩa vụ dependency nằm trong [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
