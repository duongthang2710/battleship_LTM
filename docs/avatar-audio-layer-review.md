# Review avatar và âm thanh — 29/10/2026

## Thay đổi

- `frontend/src/main/java/com/battleship/frontend/game/BattleHud.java`: tách avatar,
  viền và cung timer vào lớp foreground; thêm đĩa nền kín dưới chân dung đối thủ.
  Toàn bộ viền/nền của danh sách tàu ở phía sau, kể cả chỗ PNG avatar trong suốt.
  Giữ kích thước, vị trí và độ mờ dải trắng vừa chốt.
- `frontend/src/main/java/com/battleship/frontend/audio/GameAudio.java`: trước khi
  phát pháo/va chạm mới, dừng đuôi các tiếng chiến đấu trước đó. Victory/Defeat
  dừng đuôi chiến đấu và cue kết quả/cảnh báo lượt cũ. Không đổi volume hoặc nhạc.
- Xóa `tools/audio/generate_sounds.py`: generator procedural cũ có thể ghi đè
  chín WAV mới. Cập nhật `sounds/ORIGIN.md` để chỉ dẫn dùng importer hiện tại.
- Cập nhật `CombatPresentationChecks` kiểm tra tất cả geometry của dải tàu đều
  nằm sau portrait và đĩa nền nằm giữa; `AudioChecks` kiểm tra chỉ một tiếng
  chiến đấu/kết quả chạy sau mỗi lần chuyển cue.

## Kết luận kiểm tra code

Không tìm thấy hai hệ thống phát âm thanh song song trong ứng dụng hiện tại.
GameAudio dùng một bộ WAV; không có fallback sinh âm procedural khi chạy.
Đã đối chiếu SHA-256 của cả 34 WAV với manifest: không file nào khác bộ nhập mới.
Các WAV mới có đuôi dài (Sunk khoảng 10 giây), có thể tiếp tục phát cùng sự kiện
kế tiếp; thay đổi lần này chặn sự chồng đuôi đó, không xóa nhầm asset mới.

Không thay backend, protocol, gameplay, background hoặc các model 3D.

## Kiểm thử thực tế

- `:frontend:classes` và `:frontend:checkCombatPresentation`: compile và render
  OpenGL đạt; kiểm tra thứ tự lớp nền/viền/đĩa kín/portrait đạt. Đã xem ảnh render
  `build/combat-review/ocean-projectile.png`.
- `:frontend:checkGameAudio`: Java Sound trên thiết bị thật đạt: chuyển lần lượt
  Cannon/Hit/Sunk/Miss/Victory/Defeat không để nhiều tiếng chiến đấu/kết quả chạy
  đồng thời; nhạc/môi trường, volume và mute/unmute vẫn qua kiểm tra hiện có.
- Lần chạy đầu phép đo audio đọc trạng thái line ngay sau `start()` bị quá sớm;
  đã đổi test sang chờ trạng thái thiết bị, giống các kiểm tra native có sẵn.
  Chạy lại audio đạt. Log: `build/avatar-audio-verification.log` và
  `build/avatar-audio-final-audio.log`.
- Đây là kiểm tra tự động trên OpenGL và audio thật, không phải nghe đánh giá
  A/B hoặc chơi hai client PvP. Chưa xác định tiếng người dùng đang gọi là
  “âm cũ” thuộc sự kiện nào; không tự xóa nhạc nền hoặc môi trường mới.

## Chạy lại

Đóng client cũ rồi chạy `./gradlew.bat :frontend:run`. Chỉ một client đang chạy
sẽ tránh nghe trùng âm của hai cửa sổ khi demo cùng máy.
