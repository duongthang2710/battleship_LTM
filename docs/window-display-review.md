# Review cửa sổ và nhiều màn hình

Nguyên nhân đã xác nhận của việc thiếu nút cửa sổ: cấu hình cá nhân lưu
`fullscreen=true`, khiến app mở lại ở fullscreen không viền. Không khẳng định
đây là nguyên nhân duy nhất của hiện tượng trải qua hai màn hình trước đó.

## Thay đổi

- `DisplaySettings`: chọn màn hình chứa phần lớn cửa sổ thay vì luôn dùng màn hình chính.
  Giới hạn kích thước theo vùng làm việc của màn hình đó; tính cả viền và thanh tiêu đề
  khi đưa cửa sổ về trong màn hình. Windowed bật lại decoration và khả năng resize.
- `DisplayModeInput`: Alt+Enter hoạt động toàn ứng dụng; không lặp khi giữ phím và
  chặn Enter của tổ hợp này đi xuống form đăng nhập/chat. Enter bình thường vẫn giữ hành vi cũ.
- `BattleshipApplication`, `ScreenController`: đăng ký shortcut trước các listener UI,
  xử lý đổi chế độ sau khi dispatch input, qua SettingsController.
- `SettingsController`: dùng luồng Apply/Save hiện có để đổi chế độ và lưu ngoài render thread.
  Kích thước lưu được điều chỉnh về kích thước hỗ trợ trên màn hình hiện tại.
- Cấu hình cá nhân hiện tại đã chuyển về Windowed; các tùy chọn khác được giữ nguyên.
  Bản sao trước khi đổi nằm trong `build/window-review/user-settings-before.properties`.

## Kiểm tra thực tế

- `gradlew build :frontend:checkDisplayWindows`: thành công.
- Kiểm thử native trên **cả 2 màn hình đang kết nối**: cửa sổ/viền/thanh tiêu đề nằm trong
  một màn hình; thu nhỏ, phóng to, khôi phục hoạt động; fullscreen đúng màn hình hiện tại;
  quay về Windowed và lưu cấu hình; giữ cùng native window/OpenGL context; đóng cửa sổ kiểm thử.
- Kiểm tra input: Alt+Enter chỉ phát một yêu cầu, giữ Enter không lặp, không gửi form;
  Enter thường vẫn được chuyển cho UI.
- Đã mở frontend thật và nhận CONNECT_OK. Dùng computer-use quan sát trực tiếp và thử
  Alt+Enter: app trở về Windowed 1600×900, có thanh tiêu đề và đủ nút thu nhỏ/phóng to/đóng.
  Để nguyên ứng dụng đang mở cho người dùng.
- Log build/kiểm thử: `build/display-window-verification.log`.
  Log frontend thật: `build/window-review/frontend-windowed.log`.

Chưa kiểm tra nhiều màn hình có DPI khác nhau hoặc native macOS trong lượt này.
Trạng thái tại thời điểm review ban đầu: chưa commit. Đã chia commit ngày 29/10/2026: `eb93b89`, `0f39ac1`; chưa push. Không chỉnh visual, gameplay hay backend cho sửa lỗi cửa sổ.

## Bổ sung 29/10/2026: con trỏ sau Alt+Tab

- `BattleshipApplication.gainFocus()`: đưa thao tác khôi phục sang render thread, reset trạng thái
  phím/nút trong InputManager, bật lại con trỏ ở cả engine và native mouse input, gắn lại con trỏ hệ thống.
  Cần gọi native mouse input trực tiếp vì InputManager bỏ qua `setCursorVisible(true)` nếu cache đã là true.
- `BattleshipApplication.loseFocus()` và `DisplayModeInput.reset()`: xóa Alt/phím Enter đang capture
  và yêu cầu chuyển chế độ còn chờ, tránh kẹt tổ hợp Alt+Enter sau khi chuyển ứng dụng.
- Không ép trạng thái chuột mỗi frame và không đưa chuột về tâm màn hình.
- `SettingsChecks`: xác nhận mất focus khi đang giữ Alt không khiến Enter thường đổi fullscreen sau đó.
- `CursorFocusChecks`/task `checkCursorFocus`: khởi động production BattleshipApplication với file cấu hình
  kiểm thử riêng; dùng một cửa sổ native phụ để thực sự chuyển focus ra/vào. Đạt 3 vòng Windowed và
  3 vòng Fullscreen, gồm cả trạng thái native HIDDEN/DISABLED trong khi cache engine vẫn báo visible.
  Sau mỗi lần trở lại: native cursor NORMAL, engine visible; callback chuột GLFW vẫn cập nhật InputManager.
  Di chuyển chuột trong kiểm tra dùng callback injection, không khẳng định kiểm tra chuyển động chuột vật lý.
- Kiểm tra native này không phải thao tác Alt+Tab thủ công. Chưa kiểm tra riêng macOS trong lượt sửa này.
- `gradlew build`: thành công; `gradlew :frontend:checkCursorFocus`: thành công.
  Log cuối: `build/cursor-focus-build.log` và `build/cursor-focus-native.log`.
- Cần đóng và chạy lại client đang mở để nạp bản sửa; kiểm thử không đóng phiên client của người dùng.

## Sửa hồi quy khởi động Windowed — 29/10/2026

Lần sửa trước mới đổi cấu hình cá nhân sang `fullscreen=false`; `SettingsController.loaded()`
vẫn áp dụng trực tiếp giá trị fullscreen trên đĩa. Khi người dùng bật fullscreen rồi mở lại app,
luồng tải bất đồng bộ lại bỏ thanh tiêu đề dù AppSettings ban đầu là Windowed.

- `ClientPreferences.forStartup()` và `SettingsController.loaded()`: mọi lần khởi động đều dùng
  Windowed. Vẫn giữ kích thước và độ nhạy đã lưu. Settings/Alt+Enter có thể bật fullscreen trong phiên.
- README và thông báo Save đã ghi rõ chính sách mới; fullscreen không còn tự khôi phục lần mở tiếp theo.
- `SettingsChecks`: profile fullscreen=true được chuẩn hóa khi khởi động, giữ nguyên các giá trị còn lại.
- `DisplayWindowChecks`: cố ý lưu fullscreen=true trước khi nạp SettingsController, kiểm tra Windowed
  ngay khi tải xong và trước mọi thao tác đổi chế độ khác. Đạt trên cả hai màn hình với native title bar,
  thu/phóng/khôi phục và fullscreen đúng màn hình. Kiểm tra này sẽ thất bại với luồng tải cũ.
- Kiểm tra restart trong `PvpRoomRenderChecks` đã cập nhật kỳ vọng mới; chưa chạy lại toàn bộ bài hai client desktop đó trong lượt này.
- `gradlew build :frontend:checkDisplayWindows`: thành công, log `build/window-startup-regression.log`.
- Đã mở qua `gradlew :frontend:run` với **file cá nhân vẫn lưu fullscreen=true, không chỉnh lại file**;
  quan sát bằng computer-use thấy đúng một cửa sổ 1600×900 với thanh tiêu đề và ba nút Windows.
  Frontend nhận CONNECT_OK; để app đang mở. Log `build/window-startup-frontend.log`.

## Sửa hình con trỏ fullscreen — 29/10/2026

Phản hồi tiếp theo của người dùng làm rõ: vẫn bấm được, nhưng không thấy hình chuột.
Các kiểm tra cờ native/engine trước đó chưa đủ để kết luận hình chuột thực sự hiện trên màn hình.

- Thêm `ui.FullscreenCursor`: fullscreen dùng mũi tên được render trong GUI, hai geometry tĩnh
  viền tối/lõi sáng, nằm trên các panel. Không có texture/asset mới hoặc hiệu ứng nặng.
- Native cursor dùng `GLFW_CURSOR_HIDDEN` khi fullscreen và có focus; đây là ẩn hình,
  không phải `GLFW_CURSOR_DISABLED` khóa/bắt chuột. Input và thao tác bấm giữ nguyên.
- Vị trí vẽ lấy từ tọa độ native hiện tại và đổi sang framebuffer; không phụ thuộc một
  mouse-motion callback có thể chưa đến sau focus/resize/warp. Điểm nhọn là hotspot.
- Khi mất focus, thu nhỏ, chuột sang màn hình khác hoặc về Windowed, ẩn lớp cursor GUI;
  trả chuột native bình thường khi không còn dùng cursor fullscreen.
- `BattleshipApplication`: cập nhật lớp cursor sau UI, khôi phục đúng chế độ khi gainFocus,
  dọn lớp cursor và trả native cursor khi đóng app. Chính sách startup Windowed vẫn giữ.
- `CursorFocusChecks`: tiếp tục chuyển focus native 3 vòng Windowed + 3 vòng Fullscreen;
  kiểm tra ảnh framebuffer sau mỗi lần trở lại fullscreen. Cả 3 ảnh có **103 pixel sáng**
  của mũi tên trên nền kiểm thử tối; di chuyển thì vùng cũ còn dưới 5 pixel sáng.
  Kiểm tra thêm vị trí vẽ sau native warp không có motion callback. Windowed không vẽ cursor GUI trùng.
- `gradlew build :frontend:checkCursorFocus`: thành công. Log cuối `build/software-cursor-verification.log`;
  ảnh trong `build/window-review/cursor-fullscreen-*.png` và `cursor-moved-*.png`.
  Hình chữ nhật tối dưới mũi tên chỉ thuộc fixture kiểm tra pixel, không có trong production.
- Đã mở lại client cuối qua launcher, bật fullscreen, dùng Alt+Tab thật sang ứng dụng khác,
  activate lại Battleship và quan sát con trỏ được vẽ. Sau kiểm tra đưa app về Windowed và để mở.
  Log client cuối: `build/software-cursor-final-frontend.log`.
- Chưa kiểm tra macOS hoặc hai màn hình có DPI khác nhau trong lượt này. Không thay gameplay hoặc visual scene.
