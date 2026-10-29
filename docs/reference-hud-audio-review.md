# Review HUD theo ảnh và âm thanh — 29/10/2026

## Phạm vi đã chốt

Người dùng xác nhận **giữ bàn cờ 3D, đổi HUD theo hai ảnh gửi kèm**.
Không chuyển bàn chính sang lưới 2D; không đổi background, castle, ocean, ship model,
camera, lighting, backend, shared hoặc protocol trong lượt này.

## UI

- Banner xanh dọc bên trái: tên tài khoản, avatar và danh sách tàu khi placement.
- Header giữa trên: Deploy Fleet khi xếp tàu; Your Turn/Enemy Turn khi Battle.
- Trạng thái Ready/Deploying của đối thủ và dấu check xanh dùng `opponentSubmitted` từ snapshot server; không tự suy đoán từ UI.
- Ready ở giữa phía dưới; khung hướng dẫn và Random tròn phía phải; giữ thao tác
  chọn/đặt/kéo thả/xoay/xóa/reset/random/Designer của controller hiện có.
- Battle: minimap chuyển lên trên bên trái; dãy kích thước tàu địch bên phải;
  avatar và tên đối thủ ở góc dưới bên phải. Chỉ đánh dấu tàu đã được server xác nhận
  chìm, không suy ra loại tàu của các hit chưa được công khai.
- Hai chân dung được tạo riêng bằng imagegen, thay đổi theo phe trắng/đen.
- Timer là cung dưới avatar, 30 phân đoạn; cung người đang có lượt giảm theo
  `PvpController.remainingTurnMillis()`. Đổi người theo ID, không so sánh tên hiển thị.
  Không dùng timer UI để quyết định thắng thua.
- Hit/Miss/Sunk hiển thị trên huy hiệu chỉ huy trong khoảng trình diễn phát bắn.
- Bánh răng mở Settings thật. Đóng Settings quay lại trận đang có; không restart
  hoặc rời phòng. Network và tiến trình hiệu ứng vẫn cập nhật khi Settings mở;
  đồng hồ server không bị dừng. Chat được đặt dưới banner để tránh đè avatar.
- Font HUD dùng Roboto Condensed Bold, có glyph tiếng Việt và giấy phép OFL;
  font của menu khác và nhãn 3D trên board giữ nguyên.

Không thêm Mine/Sonar/Bomb/Airstrike từ ảnh mẫu vì game chưa có các khả năng đó;
vị trí danh sách bên trái phục vụ các loại tàu thật. Đây là bố cục UI theo ảnh,
không phải bản sao asset hoặc bàn cờ 2D của game tham chiếu.

## Âm thanh

**Cập nhật:** bộ procedural trong phần ghi nhận ban đầu dưới đây đã được thay
bằng 34 WAV tải có giấy phép, gồm nhạc nền và môi trường theo yêu cầu tiếp theo.
Xem [downloaded-audio-review.md](downloaded-audio-review.md) và `sounds/ORIGIN.md`.

Thêm 9 WAV nguyên gốc: click, đặt tàu, Ready, pháo, trúng, nước bắn, chìm, thắng,
thua. **Không lấy hoặc trích âm thanh Steam**: repo không có bộ asset âm thanh gốc
để nhập; bộ đang tích hợp là tổng hợp procedural mới, không khẳng định giống hệt
âm thanh của game đó. Không thêm nhạc nền.

`GameAudio` đọc file, mở thiết bị và phát trên executor riêng. Volume sử dụng gain
PCM bằng phần mềm, không phụ thuộc mixer có hỗ trợ MASTER_GAIN. Mute dừng âm đang
phát và chặn cue mới. Không sửa âm lượng hệ thống. Thiếu thiết bị âm thanh thì log
và tiếp tục chạy game; không làm render thread chờ. Settings lưu volume/mute vào
file cũ và vẫn đọc được profile chỉ có display/camera của phiên bản trước.

## Các file chính

| Nhóm | File |
| --- | --- |
| HUD, input | `game/BattleHud.java`, `game/PlacementHud.java`, `game/GameController.java` |
| Vẽ UI | `ui/NavalHudSkin.java` mới, `ui/UiTypography.java` |
| Vị trí UI phụ | `game/OwnBoardMinimap.java`, `chat/ChatPanel.java` |
| Điều hướng Settings | `app/ScreenController.java` |
| Âm thanh | `audio/GameAudio.java` mới, `resources/sounds/*.wav`, `sounds/ORIGIN.md` |
| Lưu cấu hình | `settings/ClientPreferences.java`, `SettingsStore.java`, `SettingsController.java`, `SettingsPanel.java`, `DisplaySettings.java` |
| Asset mới | `textures/portraits/*.png`, `portraits/ORIGIN.md`, `fonts/NavalHud.*`, `NavalHud-OFL.txt` |
| Công cụ tạo asset | `tools/audio/generate_sounds.py`, `tools/fonts/generate_hud_font.py` |
| Checks | `audio/AudioChecks.java` mới; cập nhật CombatPresentation, GameplayRender, TypographyRender và SettingsChecks; task `checkGameAudio` trong `frontend/build.gradle` |
| Hướng dẫn | `README.md`, tài liệu review này |

Các đường dẫn Java/resources trong bảng nằm dưới `frontend/src/main/`; checks nằm
dưới `frontend/src/test/`. Những thay đổi visual chưa commit từ các lượt trước được
giữ nguyên, không gộp thành công việc HUD mới.

## Kiểm thử đã chạy

Log `build/reference-hud-verification.log`: **BUILD SUCCESSFUL**.

- `build`: compile toàn bộ frontend/backend/shared và các checks đang gắn với build;
  có kiểm tra lưu/đọc volume + mute và dữ liệu cấu hình không hợp lệ.
- `checkCombatPresentation`: OpenGL thật; UI hit target ở ba kích thước;
  cung đầy/nửa/hết, đổi chủ lượt; placement lock/draft; hit/miss/sunk và xác tàu.
- `checkGameplayRendering`: chơi hết một trận bằng input/render fixture; đặt/kéo
  thả/xoay, bắn, chuyển lượt, game over và restart; nút Settings phát callback trong
  lúc đạn bay không đổi GameState; một cue pháo và một cue hit đúng lúc va chạm.
- `checkTypographyRendering`: 20 màn hình mỗi cỡ 1440×900, 1280×800, 960×600.
- `checkGameAudio`: giải mã 9 WAV không im lặng, PCM/gain đúng; **thiết bị Java Sound
  thật trên Windows phát được**, mute dừng, unmute phát lại. Đây là kiểm tra line
  đang chạy, không phải đánh giá nghe chủ quan chất lượng âm thanh.

Qua ảnh render đã phát hiện và sửa màu UI bị nhạt do gamma, icon bánh răng bị lớp
nền che và chữ hướng dẫn bị cắt. Render kiểm tra lại trong
`build/reference-hud-final-render.log` (**BUILD SUCCESSFUL**). Có kiểm tra render bổ sung
trạng thái Ready và timer trong `build/reference-hud-ready-check.log`. Ảnh ở `build/combat-review/` và `build/font-review/`.

Chưa kiểm thử thủ công hai client PvP đăng nhập thật hoặc trên macOS trong lượt này.
Luồng mở Settings rồi quay lại trận qua ScreenController chưa được chạy trọn bằng
hai client thật; đã kiểm tra callback không làm đổi state và đã review đường điều
hướng không gọi restart/leave. Không khẳng định những phần chưa chạy là đã pass.

Chạy client: `./gradlew.bat :frontend:run`; mỗi máy dùng cùng phiên bản source.
