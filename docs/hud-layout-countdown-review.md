# Review sửa layout và countdown — 29/10/2026

## UI đã sửa

- `pvp/PvpRoomPanel.java`: thay COPY lớn bằng icon hai tờ giấy 16 px, vùng bấm
  24×24 logical px ngay góc phải trên mã phòng. Giữ chức năng clipboard, hover và
  hit target theo scale. Tăng khoảng cách dòng luật chơi với Ready; tách vùng
  thông báo khỏi các nút; giới hạn tên trong phần danh sách người chơi.
- `game/BattleHud.java`: bỏ thanh Waiting for opponent sau khi chốt đội hình và
  hai thanh Your fleet/Your turn trong Battle. Bỏ cả vùng chặn chuột của các thanh
  đã xóa. Giữ thông tin lượt bằng cung thời gian avatar và trạng thái cần thiết.
- `game/OwnBoardMinimap.java`: viền vàng mảnh chạm sát mép grid; chuyển vào cột trái
  ngay phía trên chat. Không thay vật liệu, mapping ô hoặc hướng của minimap.
- `chat/ChatPanel.java`, `ui/BattleSidebarLayout.java` mới: dùng cùng mốc bố cục với
  banner/minimap. Thu ngắn phần đuôi banner Battle để xếp gọn cột trái. Chat giữ
  nguyên cạnh trên; cả thao tác bấm mở và Enter focus đều mở xuống dưới. Giữ thanh
  cuộn và các thao tác chat cũ; kích thước 960×600 vẫn còn đủ chỗ cho ô nhập.
- `ui/NavalHudSkin.java`: thêm nền trắng trong suốt 14% theo đúng silhouette phía
  sau dãy tàu địch. Đây là UI phẳng trong GUI, không sửa environment hoặc ánh sáng.
- `auth/AccountPanel.java`, `friends/FriendsPanel.java`,
  `leaderboard/LeaderboardPanel.java`, `pvp/GameInvitePanel.java`,
  `settings/SettingsPanel.java`, `game/GameOverOverlay.java`: rà khoảng cách chữ;
  giới hạn tên dài theo chiều rộng thực tế, tăng khoảng cách hai dòng leaderboard,
  nới title/description đăng nhập và section Settings. Card kết thúc cao hơn 16 px;
  tên người thắng cách trạng thái rematch rõ hơn, Decline và thông báo có cột riêng.

Không sửa board/castle/background/ocean/ships/camera hay luật tấn công.

## Countdown do server kiểm soát

- `shared/match/TurnTimer.java`: bổ sung `startsInMillis`, giữ constructor 4 tham số
  để tương thích các call site và test cũ; thời gian chuẩn bị là 3.000 ms.
- `backend/match/MatchService.java`: khi nhận đủ hai đội hình, lưu thời điểm START
  bằng monotonic clock. Trong chuẩn bị, timer lượt giữ 60.000 ms, bắn sớm bị từ chối
  và không tăng revision/không đổi bàn cờ. Các tick 3/2/1/START dùng TURN_TIMER hiện có.
  Sau START và sau mỗi phát hợp lệ, vẫn áp dụng giới hạn 60 giây hiện có. Hết giờ
  và đòn bắn vẫn được xử lý dưới cùng lock của RoomManager.
- `frontend/pvp/PvpController.java`: tính thời gian chuẩn bị còn lại và chỉ trừ
  thời gian lượt sau khi hết delay server cung cấp; không dùng wall clock.
- `frontend/game/GameController.java`: khóa bắn trong countdown, hiện START ngắn
  rồi mở input; nút Settings vẫn hoạt động. Clear intro khi rời trận, vào ván mới
  hoặc game over. Không Thread.sleep, không block render/network thread.
- `frontend/game/BattleHud.java`: chữ 3–2–1–START ở giữa màn hình. Rematch dùng
  MatchService mới nên không dùng lại deadline/countdown cũ.

Backend và cả hai client cần chạy cùng bản mới; chỉ chạy lại frontend với server
cũ sẽ không có dữ liệu delay để hiển thị countdown.

## Kiểm thử thực tế

- `build/hud-layout-countdown-verification.log`: **BUILD SUCCESSFUL**, toàn project.
  MatchTimerChecks kiểm tra countdown 3/2/1, reject bắn sớm, đủ 60 giây tại START,
  reset sau phát hợp lệ, phát sai/trùng không reset, timeout đúng biên, timer cũ
  không ảnh hưởng lượt mới. RoomRulesChecks qua 12 cấu hình phòng.
- OpenGL CombatPresentationChecks và GameplayRenderChecks qua: 3/2/1/START render,
  không còn header đã yêu cầu bỏ, haze chỉ nằm trong GUI, đặt/di chuyển/bắn/chìm,
  Game Over và Restart. Copy icon có kiểm tra hit target/clipboard bằng UiFeedbackChecks.
- TypographyRenderChecks render **24 trạng thái** ở 1440×900, 1280×800 và 960×600,
  gồm menu, đăng nhập/đăng ký, Settings, tạo/join/waiting, Friends, Leaderboard,
  chat, invite, placement, Battle, countdown và Game Over. Đã xem ảnh các nhóm này
  để phát hiện và sửa khoảng cách chữ; không suy ra mọi chuỗi cực dài đã được thử.
- Kiểm tra chat xác nhận cạnh trên của nút thu gọn không đổi sau khi mở, ô nhập
  vẫn trong cửa sổ và không đè minimap. Viền minimap không tạo khoảng đệm rộng.
- `build/countdown-tcp-verification.log`: **BUILD SUCCESSFUL**, server thật cổng
  ngẫu nhiên + hai client TCP + PostgreSQL Docker đang healthy. Cả hai nhận delay,
  bắn trước START bị từ chối, chờ tick START rồi đấu được; hai ván Designer/rematch
  và một ván Basic hoàn thành, đội hình riêng tư và timer reset đúng. Test chỉ xóa
  tài khoản/trận QA vừa tạo. Có warning presence SQL 08001 lúc server test đang đóng;
  bài test vẫn hoàn tất, không sửa hệ thống bạn bè ngoài phạm vi.
- Kiểm tra render bổ sung sau sửa spacing: `build/hud-spacing-final-review.log`,
  **BUILD SUCCESSFUL**. Bản sửa cuối card kết thúc/đăng nhập:
  `build/hud-text-final-verification.log`, **BUILD SUCCESSFUL**; render lại 24 trạng
  thái ở ba kích thước và GameplayRenderChecks xác nhận Main Menu/Restart vẫn bấm
  đúng sau khi tăng chiều cao card kết thúc.

Phân biệt: hai client TCP ở trên là peer test chạy protocol thật, **không phải hai
cửa sổ client 3D được điều khiển thủ công**. 3D dùng fixture OpenGL riêng. Chưa kiểm
tra trên hai máy hoặc macOS trong lượt này. Ảnh xuất tại `build/font-review/` và
`build/combat-review/`.

Các thay đổi từ các lượt trước vẫn đang ở working tree; lượt này không tạo commit.
