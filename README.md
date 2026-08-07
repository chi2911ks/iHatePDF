# iHatePDF Android Library

Thư viện Android chuyển đổi PDF ↔ Word hoàn toàn offline, không gọi server và không tạo watermark.

[![JitPack](https://jitpack.io/v/chi2911ks/iHatePDF.svg)](https://jitpack.io/#chi2911ks/iHatePDF)

## Cài đặt

Thêm JitPack vào `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.chi2911ks.iHatePDF") }
        }
    }
}
```

Thêm dependency:

```kotlin
dependencies {
    implementation("com.github.chi2911ks.iHatePDF:pdf-word-converter:v1.0.3")
}
```

Yêu cầu `minSdk 26`. OCR ML Kit được tải như dependency của thư viện và chạy offline trên thiết bị.

## Sử dụng

```kotlin
val converter = AndroidDocumentConverter(context)

val editable = converter.pdfToDocx(
    inputPdfUri,
    outputDocxUri,
    PdfToDocxOptions(
        mode = PdfToDocxMode.EDITABLE,
        preservePageGraphics = true,
    ),
)

val visual = converter.pdfToDocx(
    inputPdfUri,
    outputDocxUri,
    PdfToDocxOptions(mode = PdfToDocxMode.VISUAL),
)

val pdf = converter.wordToPdf(inputWordUri, outputPdfUri)
```

Gọi API từ coroutine hoặc worker ngoài main thread.

- `EDITABLE`: dùng PDFBox lấy text/font/toạ độ và giữ image/vector/paint bằng graphics layer.
- `VISUAL`: giữ giao diện trang tốt hơn nhưng nội dung trang là ảnh.
- Word input hỗ trợ `.docx` và `.doc`.
- Scan PDF được OCR bằng ML Kit.

## Build và publish local

```powershell
.\gradlew.bat :poi-source-engine:publishToMavenLocal `
  :offline-document-engine:publishToMavenLocal `
  :pdf-word-converter:publishToMavenLocal -x test
```

JitPack dùng [jitpack.yml](jitpack.yml) để tạo ba artifact. Artifact public dành cho người dùng là `pdf-word-converter`; hai engine còn lại được kéo tự động qua POM.

## Kiểm thử

```powershell
.\gradlew.bat :pdf-word-converter:testDebugUnitTest
.\gradlew.bat :pdf-word-converter:connectedDebugAndroidTest
```

Xem phạm vi và roadmap tại [PDF_WORD_ANDROID_LIBRARY_PLAN.md](docs/PDF_WORD_ANDROID_LIBRARY_PLAN.md). Nghĩa vụ dependency nằm trong [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
