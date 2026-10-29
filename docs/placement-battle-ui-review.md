# Review UI đặt tàu, Battle và xác tàu — 29/10/2026

## Phạm vi và tham khảo

Tham khảo các khung gameplay trong trailer chính thức của
[Hasbro's BATTLESHIP trên Steam](https://store.steampowered.com/app/893050/battleship?l=english),
đặc biệt khu vực trạng thái lượt, bàn chính và phản hồi hit/miss ở khoảng 8–32 giây.
Đã trích và xem các khung hình video, không chỉ đọc trang cửa hàng. Bản trích phục vụ
tham khảo nằm trong `build/steam-reference/`, không đưa asset Steam vào game.
Giữ màu navy/vàng, font và đường viền fantasy của project.

## Những thay đổi trong lượt này

| File | Thay đổi và trách nhiệm |
| --- | --- |
| `frontend/.../game/PlacementHud.java` | Bỏ panel dọc nhiều chữ; dùng thanh ngang chọn 3–5 tàu. Biểu tượng và số vạch thể hiện chiều dài/trạng thái đặt. Giữ READY và Random ở ngoài; Rotate, Remove, Reset, Leave nằm trong `...`. Designer giữ Draw/Move. Vùng click và vùng chặn raycast theo cùng layout đã scale. |
| `frontend/.../game/BattleHud.java` | Một thanh phía trên gồm hai người chơi, trạng thái giữa và đồng hồ gọn bên dưới. Bỏ tiêu đề game lớn, viền toàn màn hình và các hộp hướng dẫn/kết nối lặp. Giữ thông báo mất kết nối ở overlay hiện có. |
| `frontend/.../game/GameController.java` | Kết nối menu phụ; ưu tiên hiển thị Volley incoming → HIT/MISS/Ship sunk → lượt tiếp theo. Snapshot vẫn có hiệu lực chính thức nhưng chỉ cập nhật tàu/marker lúc va chạm. Minimap dùng trạng thái trước phát bắn trong giai đoạn đạn đang bay. |
| `frontend/.../effects/AttackEffectManager.java` | Tổng trình diễn 3,4 giây, va chạm ở giây 1. Có API đọc kết quả đang trình diễn; khóa thao tác bắn bằng cơ chế busy hiện có. |
| `frontend/.../effects/HitEffect.java`, `MissEffect.java` | Tách thời gian sống hiệu ứng 1,1 giây khỏi thời gian giữ kết quả; không kéo dài nổ/nước thành chuyển động chậm. |
| `frontend/.../graphics/ship/ShipWreckBuilder.java` (mới) | Xác tàu ám cháy, thân thấp và nghiêng nhẹ, mảnh giáp/sào gãy, vết cháy, than hồng và ba cụm khói nhỏ. Tạo bản sao material trước khi chỉnh; không nhuộm lại template tàu nguyên vẹn. |
| `frontend/.../game/FleetRenderer.java` | Cache phân biệt INTACT/SUNK. Chỉ dựng xác khi model tàu đã được phép hiển thị và `Ship.isSunk()` đúng. Tạo lại model nguyên vẹn ở ván mới. |
| `frontend/src/test/.../CombatPresentationChecks.java` | Kiểm tra UI, hit target, khoảng cách tiêu đề, giữ kết quả, xác tàu hai phe/custom/basic, giấu tàu chưa chìm và reset model. Xuất ảnh OpenGL. |
| `frontend/src/test/.../DesignerRenderChecks.java`, `ui/TypographyRenderChecks.java` | Cập nhật kiểm tra theo thanh tàu mới; vẫn kiểm tra số tàu theo phòng và font. |
| `README.md` | Cập nhật cách mở thao tác phụ và giải thích nhịp phát bắn. |

Các file board/environment/custom-ship factory đang có thay đổi chưa commit từ lượt
visual trước được giữ lại; lượt UI này không sửa thêm các file đó. Không sửa backend,
shared, luật chơi, protocol, asset background, camera, lighting hay thiết kế minimap.

## Hành vi cần lưu ý

- Chọn tàu, click đặt, kéo thả di chuyển, T xoay, C reset, F random và Enter xác nhận vẫn dùng controller/input hiện có.
- Không cho READY khi thiếu tàu, còn draft hoặc đang gửi/chờ server xác nhận đội hình.
- Thanh tác vụ Battle chỉ giữ chuyển bàn và Leave. Chat và minimap giữ vị trí/chức năng cũ.
- Đạn bay khoảng 1 giây; sau va chạm giữ kết quả khoảng 2,4 giây. Hết khoảng trình diễn mới mở lại thao tác bắn và chuyển bàn quan sát.
- **Đây là khoảng dừng trình diễn trên client, không phải thay đổi luật thời gian server.** Server vẫn đổi lượt khi xử lý phát bắn hợp lệ và đồng hồ 60 giây vẫn chạy thật; UI không giả vờ đóng băng đồng hồ. Không thêm cooldown vào protocol/server.
- Server vẫn quyết định hit/miss/sunk và thắng thua. Xác tàu dựa vào dữ liệu đã xác nhận, không suy đoán hoặc lộ tàu chưa chìm.
- Xác tàu là hiệu ứng hình học nhẹ và khói procedural; chưa có mô phỏng gãy thân, vật lý hoặc chìm xuống nước.

## Kiểm thử thực tế

`build/placement-ui-verification.log`: **BUILD SUCCESSFUL**, gồm:

- `build`: compile frontend/backend/shared và các checks tự động đã gắn vào build.
- `:frontend:checkCombatPresentation`: render OpenGL thật; hit target ở 1280×800, 1600×900, 1920×1080; Ready/draft/locked; giữ kết quả sau khi burst tắt; xác tàu hai phe, custom/basic; chỉ hiện tàu chìm; đổi về đội hình nguyên vẹn không còn khói cũ.
- `:frontend:checkGameplayRendering`: input/render fixture chơi một trận local thủ công hai bên bằng script, kiểm tra đặt/kéo thả/xoay, hit/miss/sunk, thắng và Restart; camera/minimap/castle anchors vẫn giữ.
- `:frontend:checkDesignerRendering`: drawing/kéo thả/xoay và phòng 3 tàu; 96 mẫu mỗi phe có mesh hữu hạn, nằm trong footprint.

Khi xem ảnh đầu tiên đã phát hiện tiêu đề thanh tàu chạm thẻ tàu; đã sửa khoảng cách
và thêm kiểm tra bounding box tiêu đề không giao tên tàu. Kiểm tra render cuối được
ghi riêng trong `build/placement-ui-final-render.log`: **BUILD SUCCESSFUL**;
CombatPresentationChecks qua, TypographyRenderChecks render 20 màn hình mỗi kích thước
1440×900, 1280×800 và 960×600. Đã xem ảnh placement và Battle để đối chiếu khoảng cách
chữ, thanh tác vụ và khung chat.

Đây là các JVM/render fixture dùng OpenGL thật, **không phải thao tác thủ công hai
client 3D đăng nhập PvP qua PostgreSQL**. Chưa chạy lại hai cửa sổ PvP hoặc máy macOS
trong lượt này; không xác nhận các trường hợp đó chỉ từ ảnh render.

Ảnh: `build/combat-review/`, `build/gameplay-review/`, `build/designer-review/` và
`build/font-review/`. Chạy frontend: `./gradlew.bat :frontend:run`.
