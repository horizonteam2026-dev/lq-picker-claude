# Liên Quân Picker – App gợi ý chọn tướng

App Android độc lập, **chỉ gợi ý** khi bạn đang ở màn hình chọn tướng. Bạn tự nhập tướng đồng đội và đối thủ vào app, app trả về danh sách tướng nên chọn kèm lý do.

## Nguyên tắc (không phải phần mềm gian lận)
- Chỉ **đọc thông tin mà chính bạn đang nhìn thấy** (tên tướng trên màn hình chọn tướng), không đọc bộ nhớ game, không sửa game, không có thông tin ẩn.
- Không bấm, không gõ, không tự thao tác vào game (không accessibility, không overlay).
- Quét màn hình là **tuỳ chọn**, do bạn bật; hệ thống Android hỏi xác nhận; luôn có thông báo đang chạy; chỉ chụp 1 khung hình khi bạn bấm "Quét".
- Không có quyền INTERNET: ảnh được OCR ngay trên máy, không lưu, không gửi đi.
- Lưu ý: điều khoản của Garena về công cụ bên thứ ba có thể thay đổi, hãy tự xem lại trước khi dùng thường xuyên.

## Quét màn hình + bong bóng nổi
Thiết kế theo màn chọn tướng thật (15 giây): cột TRÁI là đồng đội (tên người chơi / tên tướng / danh hiệu), cột PHẢI là đối thủ (tên tướng màu đỏ), lưới tướng ở giữa là của riêng bạn nên **không đọc**.

Luồng dùng:
1. Trong app: cấp quyền "hiện trên ứng dụng khác" (một lần), chọn vị trí, bấm **Bật quét + bong bóng**, đồng ý hộp thoại ghi màn hình.
2. Vào game: bong bóng **LQ** nằm trên màn hình, kéo được. Chạm để mở bảng, chọn **vị trí bạn sẽ đánh** (bắt buộc, để tool khỏi đoán), bấm **Phân tích**.
3. Tool đọc tên tướng hai đội (đủ hay thiếu đều được; chọn sớm thì dùng chế độ "ít bị khắc, linh hoạt"), hiện top 3 tướng kèm lý giải và độ tin cậy.
4. Bảng tự thu về bong bóng mờ sau 15 giây; nút **Ẩn** gỡ hẳn (thông báo có nút **Hiện** để gọi lại).

Kỹ thuật: `MediaProjection` + `VirtualDisplay` tạm ngắt giữa các lần quét (gần như 0% CPU lúc rảnh); chỉ OCR hai dải cột trái/phải bằng ML Kit offline; bong bóng tự ẩn khi chụp để không bị đọc nhầm; bong bóng dùng View thuần (không Compose) cho nhẹ; khớp tên không dấu, sai 1 ký tự vẫn nhận (từ đơn), bỏ qua dòng tên người chơi (phòng nick trùng tên tướng). Đã kiểm tra logic khớp tên với toạ độ trích từ ảnh chụp thật: đọc đúng 3 đồng đội và 3 đối thủ.

Giới hạn cần biết:
- Chưa đọc được **tướng bị cấm** (chỉ hiện bằng icon, không có chữ; cần ảnh mẫu từng tướng). Hiện thêm tay trong app.
- Chưa biết bạn đã sở hữu tướng nào, nên có thể gợi ý tướng bạn chưa có.
- Nếu game đổi giao diện hoặc chặn ghi màn hình (hình đen) thì nhập tay trong app.
- Bố cục (cột trái/phải) mới được kiểm tra trên một ảnh; cần thử trên máy thật.

## Tính năng bản 1.0
1. Chọn vị trí của bạn: Caesar / Rừng / Giữa / Rồng / Trợ thủ.
2. Nhập 4 đồng đội, 5 đối thủ (bất kỳ thứ tự, nhập dần theo lượt chọn).
3. Nhập danh sách tướng bị cấm (tối đa 8).
4. Gợi ý tối đa 8 tướng, mỗi tướng có điểm 0–100, các lý do nên chọn và cảnh báo rủi ro.
5. Tìm tướng không dấu (gõ "diêu thuyền" hoặc "dieu thuyen" đều ra).

## Kiến trúc
```
app/src/main/
  assets/heroes.json            dữ liệu tướng (sửa được, không cần build lại code)
  java/com/lqpicker/
    Models.kt                   Hero, Traits, Lane, HeroClass
    HeroRepository.kt           đọc heroes.json
    SuggestionEngine.kt         thuật toán chấm điểm (hàm thuần, dễ test)
    DraftViewModel.kt           trạng thái màn hình
    MainActivity.kt
    scan/ScreenScanService.kt   quét màn hình + OCR
    scan/OverlayController.kt   bong bóng nổi (View thuần)
    scan/NameMatcher.kt         khớp chữ OCR với tên tướng (hàm thuần)
    scan/ScanBus.kt             cầu nối service ↔ UI
    ui/Theme.kt, DraftScreen.kt, HeroPicker.kt, ScanCard.kt
```
Kotlin + Jetpack Compose + Material3, MVVM đơn giản, không thư viện ngoài nặng (không ảnh, không mạng, không database).

## Thuật toán gợi ý
Mỗi tướng có 10 chỉ số 0–3: `tank, burst, dps, cc, mob, sus (hồi/khiên), rng (tầm xa), eng (khai giao tranh), push, prc (xuyên giáp)`, cùng loại sát thương (vật lý / phép / chuẩn) và vị trí chơi được.

Điểm = 50 + tổng các thành phần:
- **Nhu cầu đội hình**: thiếu đỡ đòn, thiếu khống chế, thiếu người khai giao tranh, thiếu sát thương chính, lệch sát thương vật lý/phép, trùng xạ thủ/trợ thủ.
- **Khắc chế đối thủ**: xuyên giáp vs tanker, burst vs tướng mỏng, khống chế vs tướng cơ động, tướng cơ động vs tướng đánh xa mỏng, tanker chịu burst.
- **Rủi ro bị khắc ngược**: bị đối thủ xuyên giáp, bị burst hạ nhanh, bị lao vào.
- **Ghi đè theo cặp** (tuỳ chọn): trường `counters` / `weak` trong JSON để bạn tự thêm cặp khắc chế cụ thể.

Đây là heuristic dựa trên chỉ số, không phải dữ liệu thống kê tỉ lệ thắng.

## Tối ưu cho máy yếu
- Không ảnh, không animation, không gradient/bóng đổ: avatar tướng vẽ bằng màu + chữ viết tắt.
- Một `LazyColumn` duy nhất, lưới chọn tướng dùng `LazyVerticalGrid`.
- Tính gợi ý chỉ vài trăm phép tính, chạy tức thì, không cần luồng nền.
- Bật R8 (minify + shrink resources) cho bản release.

## Build
1. Mở thư mục `LienQuanPicker` bằng Android Studio (Koala trở lên), chờ Gradle sync.
2. Chạy trên máy ảo hoặc điện thoại (Android 7.0+).
3. Bản release: Build > Generate Signed Bundle / APK.

## Cập nhật dữ liệu tướng
Sửa `app/src/main/assets/heroes.json`. Mỗi tướng:
```json
{"id":"nakroth","name":"Nakroth","cls":"ASSASSIN","lanes":["JUNGLE"],"dmg":"P",
 "t":{"tank":0,"burst":3,"dps":2,"cc":0,"mob":3,"sus":1,"rng":0,"eng":2,"push":0,"prc":0},
 "counters":[],"weak":[]}
```
`dmg`: P (vật lý), M (phép), T (chuẩn). `lanes`: TOP, JUNGLE, MID, ADC, SUP.

## Lộ trình phiên bản sau
- Trình chỉnh sửa tướng ngay trong app, nhập/xuất JSON.
- Lưu lịch sử ván và tự học tướng bạn chơi tốt.
- Hồ sơ "tướng thành thạo" của bạn để ưu tiên gợi ý.

## Tải và cài APK
Vào mục **[Releases](https://github.com/horizonteam2026-dev/lq-picker-claude/releases)**, tải file `LQPicker-v1.0.x.apk` mới nhất về điện thoại Android và cài (cho phép "cài ứng dụng không rõ nguồn gốc" nếu máy hỏi). Bản mới cài đè lên bản cũ được.

APK được GitHub Actions build tự động mỗi khi code thay đổi (`.github/workflows/build-apk.yml`). Khoá ký nằm trong Secrets của repo, không công khai. Nếu bạn fork và tự build, APK sẽ ký bằng khoá debug (cài được nhưng không cài đè lên bản chính thức).
