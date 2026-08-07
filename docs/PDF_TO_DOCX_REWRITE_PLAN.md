# Plan rewrite PDF → DOCX

Trạng thái implementation: production path `EDITABLE` đã chuyển sang Apache POI. Coordinate mapper, full-CTM image placement, `TEXT/SCAN/MIXED` classifier, OCR gating, output verification và transparent graphics-only layer đã được tích hợp. Layout semantic nâng cao tiếp tục là phần hardening/fidelity.

## 1. Mục tiêu

Xây lại luồng PDF → DOCX offline theo kiến trúc:

```text
PDF
├── PDFBox → text, font, tọa độ, ảnh, page metadata
├── PDFium/PdfRenderer → render trang, phân loại scan, OCR fallback
└── Apache POI → tổng hợp layout và tạo DOCX
```

Hai mode public:

- `EDITABLE`: text thật, paragraph, image và table; ưu tiên chỉnh sửa được.
- `VISUAL`: mỗi trang là ảnh; ưu tiên giống PDF.

Không dùng ảnh toàn trang trong `EDITABLE`. Nếu bổ sung `HYBRID`, bitmap chỉ được chứa graphics, không chứa text để tránh chữ lặp.

## 2. Nguyên tắc bắt buộc

1. PDFBox là nguồn dữ liệu chính cho PDF text-based.
2. Chỉ OCR trang scan hoặc trang có lượng text/confidence dưới ngưỡng.
3. Mọi tọa độ được chuẩn hóa về một hệ duy nhất: point, gốc trên-trái, đã áp dụng crop box và rotation.
4. Apache POI chịu trách nhiệm tạo package DOCX; bỏ custom ZIP/XML writer sau khi migration hoàn tất.
5. Không đặt text và ảnh vào cùng một XML container không được Word hỗ trợ.
6. Output phải được truncate trước khi ghi và phải mở kiểm tra lại sau khi tạo.
7. Mỗi thay đổi layout phải có test kiểm tra text, ảnh, page count và khả năng mở DOCX.

## 3. Data model trung gian

```kotlin
data class PdfDocumentModel(
    val pages: List<PdfPageModel>,
)

data class PdfPageModel(
    val widthPt: Float,
    val heightPt: Float,
    val rotation: Int,
    val kind: PageKind,
    val textLines: List<TextLine>,
    val images: List<PlacedImage>,
    val graphics: List<GraphicRegion>,
    val tables: List<TableModel>,
)

data class BoundsPt(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)
```

Mọi object phải dùng `BoundsPt`; không truyền trực tiếp tọa độ PDFBox hoặc pixel OCR vào writer.

## 4. Pipeline chi tiết

### Stage A — Validate và page metadata

- Kiểm tra magic bytes `%PDF`, file size, page count và giới hạn tài nguyên.
- Lấy media box, crop box, rotation và kích thước hiển thị từng trang.
- Phát hiện PDF mã hóa/password và trả lỗi có kiểu.
- Mở input bằng file descriptor/temporary seekable file để tránh đọc toàn bộ vào RAM.

Output: `PageMetadata` cho từng trang.

### Stage B — PDFBox extraction

- Lấy từng glyph với Unicode, font name, font size, bold, italic, baseline và bounding box.
- Chuẩn hóa font subset như `ABCDEF+Roboto-Bold` thành family/style.
- Gom glyph thành word, line và candidate paragraph.
- Lấy raster image cùng full current transformation matrix.
- Áp dụng crop box, page rotation, scale và coordinate conversion.
- Loại duplicate image XObject nhưng giữ từng placement riêng.
- Thu ruling line, rectangle, fill/stroke region để hỗ trợ nhận diện bảng và graphics.

Output: `ExtractedPage` chưa quyết định layout cuối.

### Stage C — PDFium render và page classification

- Render thumbnail 96–120 DPI để phân loại nhanh.
- Render OCR 200–300 DPI chỉ khi cần.
- Phân loại:
  - `TEXT`: PDFBox lấy được đủ text hợp lệ.
  - `SCAN`: gần như không có text nhưng trang có nhiều pixel content.
  - `MIXED`: có text và vùng ảnh scan.
- Không render full-resolution nếu trang text-based và không cần visual fallback.
- Giới hạn pixel/page và recycle bitmap ngay sau xử lý.

### Stage D — OCR ML Kit

- Chạy OCR chỉ cho `SCAN` hoặc region scan trong `MIXED`.
- Chuyển pixel bounding box về `BoundsPt` bằng scale X/Y.
- Giữ confidence nếu API cung cấp; đánh dấu nguồn `OCR`.
- Deduplicate OCR text với PDFBox text theo overlap và text similarity.
- Không ghi OCR text đè lên text PDFBox có confidence cao.

### Stage E — Layout reconstruction

- Gom line → paragraph dựa trên baseline, gap, indentation và style.
- Nhận diện reading order một cột/nhiều cột.
- Nhận diện list, alignment và tab stop.
- Nhận diện table từ ruling lines kết hợp alignment cluster.
- Chỉ tạo table khi confidence đủ cao; nếu thấp giữ paragraph độc lập.
- Phát hiện header/footer lặp giữa nhiều trang.
- Tính confidence cho paragraph, table và image placement.

### Stage F — Apache POI DOCX writer

- Tạo `XWPFDocument`, section và page size/margin theo từng PDF page.
- Dùng `XWPFParagraph` và `XWPFRun` cho text thường.
- Dùng indentation, spacing, line spacing, tab stop và alignment thay vì absolute textbox khi có thể.
- Dùng `XWPFTable` cho bảng thật.
- Dùng `XWPFRun.addPicture` cho inline image.
- Dùng low-level DrawingML anchor qua POI XMLBeans cho floating image.
- Anchor image relative to page; chuyển point → EMU bằng `1 pt = 12,700 EMU`.
- Tạo page/section break rõ ràng, không dùng paragraph ảnh để điều khiển phân trang.
- Không dùng VML textbox trong v1.

### Stage G — Verify output

- Mở lại output bằng `XWPFDocument`.
- Kiểm tra:
  - DOCX ZIP/package hợp lệ.
  - Text không rỗng nếu input có text.
  - Số ảnh không giảm ngoài rule đã định.
  - Số section/page break hợp lệ.
  - Relationship không dangling.
- Nếu verify thất bại, xóa output lỗi và trả `ConversionException.OutputInvalid`.

## 5. Migration từ code hiện tại

### Phase 1 — Khóa regression và coordinate system

- Thêm golden PDF có text, nhiều ảnh, crop box, rotation, bảng và scan.
- Tạo `PdfCoordinateMapper` và unit test cho 0/90/180/270 độ.
- Thêm test ghi đè cùng output URI nhiều lần.
- Giữ writer hiện tại hoạt động trong khi xây pipeline mới.

Hoàn tất khi mọi object dùng cùng hệ tọa độ và test không còn sai crop/rotation.

### Phase 2 — Intermediate model và extraction

- Tạo package `model`, `extract`, `layout`, `writer`.
- Refactor `PdfTextExtractor` trả `PdfPageModel` thay vì `OcrParagraph` trực tiếp.
- Refactor image extraction để giữ CTM và placement.
- Thêm classifier `TEXT/SCAN/MIXED`.

Hoàn tất khi có thể dump JSON/debug model của một trang và đối chiếu tọa độ.

### Phase 3 — POI writer thay custom writer

- Tạo `PoiDocxWriter`.
- Triển khai section, paragraph/run và page break trước.
- Triển khai inline/floating image tiếp theo.
- Triển khai table sau khi text/ảnh ổn định.
- Chạy song song writer cũ/mới trong test, không giữ hai writer trong public API.

Hoàn tất khi POI mở lại được output và test bắt buộc thấy đúng text + image.

### Phase 4 — OCR và mixed pages

- Chỉ gọi ML Kit theo classifier.
- Deduplicate PDFBox/OCR result.
- Thêm scan tiếng Việt, scan nghiêng và mixed page vào corpus.

Hoàn tất khi PDF text không bị OCR lặp và scan có text editable.

### Phase 5 — Tables và graphics

- Kết hợp ruling line với text alignment để tạo `TableModel`.
- Chuyển table confidence cao thành `XWPFTable`.
- Với vector/paint không thể chuyển semantic, raster hóa riêng graphics region trên nền trong suốt.
- Không raster hóa text vào graphics layer.

Hoàn tất khi giữ được line, fill, chart/vector cơ bản mà không lặp text.

### Phase 6 — Hardening và release

- Cancellation trong từng page/stage.
- Memory/pixel/file/page limits.
- Malformed PDF, ZIP bomb, encrypted PDF và output provider tests.
- Lint, unit test, instrumentation test, release build và benchmark.
- Cập nhật README, limitations, THIRD_PARTY_NOTICES và SBOM.

## 6. Test matrix bắt buộc

| Nhóm | Assertions chính |
|---|---|
| Text PDF | text, font/style, paragraph order |
| Ảnh | media tồn tại, kích thước và anchor gần đúng |
| Crop/rotation | bounds nằm trong page và đúng góc |
| Multi-column | reading order đúng |
| Table | row/column và nội dung cell |
| Scan | OCR text có trong DOCX |
| Mixed | không duplicate text |
| Overwrite | convert lặp lại cùng URI vẫn mở được |
| Multi-page | content không bị đẩy sang trang khác |
| Malformed input | lỗi có kiểu, không để output hỏng |

Ngoài assert XML, instrumentation test phải mở lại DOCX bằng Apache POI. Corpus thực cần mở bằng Microsoft Word/LibreOffice để visual review.

## 7. Definition of done

- Không còn dùng `EditableDocxWriter` custom cho production path. ✅
- PDF text-based không bị biến thành ảnh toàn trang trong `EDITABLE`.
- Test bắt buộc đọc được cả text và ảnh từ DOCX output. ✅
- Không có regression ghi đè output làm hỏng ZIP. ✅
- Image placement qua coordinate mapper có test rotation/crop; text geometry tiếp tục dùng tọa độ đã normalize từ `PDFTextStripper`. ✅
- OCR chỉ chạy cho trang classifier đánh dấu `SCAN`; regional dedup cho `MIXED` là bước fidelity tiếp theo. ✅
- Unit, instrumentation, lint, debug/release build đều pass.
- Fidelity được đo trên corpus nội bộ, không tuyên bố phần trăm nếu chưa có số đo.

## 8. Thứ tự triển khai đề xuất

1. `PdfCoordinateMapper` + regression tests.
2. Intermediate document model.
3. Refactor PDFBox text/image extraction.
4. `PoiDocxWriter` cho text + section.
5. POI floating image và output verification.
6. PDFium classifier + OCR fallback.
7. Reading order/table reconstruction.
8. Transparent graphics-only fallback.
9. Corpus benchmark và hardening.

Không triển khai lại absolute textbox trước khi paragraph/image pipeline POI đã ổn định và có test trực tiếp trên Microsoft Word.
