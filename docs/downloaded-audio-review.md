# Âm thanh tải về và tích hợp — 29/10/2026

Đã thay 9 âm procedural bằng nguồn tải có giấy phép và chuẩn bị đủ **34 WAV**:
29 hiệu ứng mono, 3 bản nhạc và 2 lớp môi trường stereo. Khoảng 42 MB.
Không lấy/trích asset âm thanh từ Steam và không đổi visual, luật hoặc protocol.

## Danh sách sử dụng

| File | Tác dụng |
| --- | --- |
| `bgm_menu.wav` | Đăng nhập, Main Menu, Friends, Leaderboard |
| `bgm_placement.wav` | Phòng PvP và xếp tàu |
| `bgm_battle.wav` | Trong trận; giữ nhạc khi mở Settings từ trận |
| `amb_ocean.wav` | Lớp sóng nền ở phòng/xếp tàu/trận |
| `amb_wind.wav` | Gió nền nhỏ ở cùng các màn hình trên |
| `click.wav` | Nhấn nút |
| `place.wav` | Đặt/kéo thả tàu và hoàn thành hình tàu |
| `ready.wav` | Gửi Ready đội hình |
| `cannon.wav` | Bắt đầu hiệu ứng phát bắn được chấp nhận |
| `hit.wav` | Va chạm trúng tàu |
| `splash.wav` | Va chạm xuống nước khi trượt |
| `sunk.wav` | Va chạm đánh chìm tàu |
| `victory.wav`, `defeat.wav` | Kết quả thắng/thua, phát một lần |
| `ui_hover.wav` | Rê qua nút Main Menu, chỉ khi đổi nút |
| `ui_back.wav` | Điều hướng Back bằng Esc |
| `ui_panel_open.wav` | Mở Settings/Friends/Leaderboard |
| `ui_panel_close.wav` | Nút Back trong Settings |
| `ui_error.wav` | Đặt tàu lỗi, Ready thiếu tàu, server từ chối yêu cầu |
| `ship_rotate.wav` | Xoay tàu thành công |
| `ship_remove.wav` | Xóa/reset tàu hoặc hủy bản vẽ |
| `fleet_random.wav` | Random đội hình một lần cho cả thao tác |
| `match_start.wav` | Kết thúc 3–2–1, hiển thị START |
| `turn_yours.wav`, `turn_enemy.wav` | Đến lượt mình/đối thủ sau khi trình diễn phát bắn |
| `target_select.wav` | Gửi mục tiêu PvP hợp lệ; tiếng pháo chờ server xác nhận |
| `timer_warning.wav` | Một lần ở 10 giây cuối lượt mình |
| `timer_timeout.wav` | Server xác nhận hết giờ; không dùng đồng hồ client để chốt thua |
| `chat_receive.wav` | Tin nhắn hợp lệ mới từ người khác; không phát lại cho tin của mình |
| `invite_receive.wav` | Lời mời vào phòng mới; thông báo Friends |
| `room_join.wav`, `room_leave.wav` | Server xác nhận người chơi vào/rời phòng |
| `connection_lost.wav` | Phiên đã đăng nhập bị mất kết nối |
| `rematch_request.wav` | Đối thủ yêu cầu chơi lại, chỉ lần chuyển trạng thái |

Tên gốc, link nguồn, tác giả, giấy phép, chỉnh sửa và hash từng WAV nằm trong
`frontend/src/main/resources/sounds/manifest.json`. Credits được đóng gói cùng
game tại `sounds/ORIGIN.md` và `sounds/licenses/`. Hai bài Scott Buckley dùng
CC BY 4.0 có ghi công; các nguồn còn lại là CC0.

## Phát âm thanh

- Hiệu ứng dùng Java Sound trên executor riêng, volume PCM bằng phần mềm.
- Nhạc/sóng/gió được stream từ WAV bằng ba worker riêng, không nạp cả bài vào RAM.
- Chọn nhạc theo màn hình là thao tác idempotent; không restart nhạc mỗi frame.
- Chuyển nhạc có ramp gain; WAV nền được xử lý nối vòng bằng crossfade.
- Volume/mute đã lưu áp dụng cả hiệu ứng lẫn nền; unmute giữ cảnh âm thanh đang chọn.
- Kết quả tắt nhạc để dành chỗ cho victory/defeat; tiếng biển vẫn còn nhỏ.
- Quay về menu hoặc xếp đội hình mới dừng các tiếng chiến đấu còn đuôi dài.
- Thông báo chat/lời mời và thao tác lặp nhanh có giới hạn tần suất.
- Thiếu thiết bị audio thì tiếp tục chơi; không đổi âm lượng hệ thống.

Không phải bản sao âm gốc Steam; chưa có đánh giá nghe A/B với game tham chiếu.

## Kiểm chứng

Đã qua `:frontend:checkGameAudio` (thiết bị Java Sound thật: phát hiệu ứng,
ba luồng nhạc/môi trường, chuyển cảnh, mute/unmute, dừng đuôi chiến đấu),
`:frontend:checkGameplayRendering` (OpenGL thật: một trận hoàn chỉnh và cue
pháo/hit đúng thời điểm), `:frontend:checkSettings` và `:frontend:installDist`.
Log: `build/downloaded-audio-verification.log`, `build/downloaded-audio-package.log`.
Môi trường offline dùng JAR đã có qua `build/audio-verification.init.gradle`;
không đổi khai báo dependency của project. Đã kiểm tra đủ 34 hash trong manifest.
Chưa thử mọi thông báo bằng hai client đăng nhập thật; các hook bám sự kiện
đã được client xác thực hiện có.
