# Review tàu tự vẽ, đạn biển, hiệu ứng và tọa độ — 29/10/2026

## Các commit đã tạo trước khi sửa visual

| Commit | Nội dung |
| --- | --- |
| `ccb38c9` | Cấu hình số tàu và luật lượt bắn do server xác thực |
| `40db70e` | Menu cấu hình phòng và placement theo số tàu |
| `eb93b89` | Windowed startup, nhiều màn hình, focus và Alt+Enter |
| `0f39ac1` | Con trỏ fullscreen được render trong GUI và kiểm tra pixel |
| `ebae66a` | README và review các phần trên |

Chưa push. Các thay đổi visual trong lượt này chưa commit. Thay đổi `.idea` của người dùng không được đưa vào các commit.

## Thay đổi chính

- `graphics/ship/DesignedShipBuilder`: tham chiếu silhouette tàu ghép đoạn trong ảnh người dùng cung cấp.
  Thân hẹp, mũi thuôn ở các đầu nhánh, các đoạn nối liên tục và bo tròn tại góc/T/chữ thập.
  Nhánh có khoảng nước trống thay cho sàn vuông kín. Vẫn dùng ivory/gold/blue và navy/violet,
  lan can, cửa sổ, cột tín hiệu/cờ nhỏ và crystal theo phong cách fantasy hiện tại.
  Carrier có sàn đáp/máy bay; Battleship/Cruiser/Destroyer có cụm pháo khác nhau; Submarine có thân áp lực và kính tiềm vọng.
  Dựng từ footprint hiện có, cache template và giữ batching; không đổi luật hình tàu hoặc các tàu Basic.
- `scene/environment/OceanFleetBuilder`: chỉ thêm anchor nòng pháo vô hình lên các tàu biển hiện có,
  không đổi mesh/vị trí/palette của chúng. Anchor đi theo rocking, rotation và scale thật của tàu.
- `effects/OceanGunfire`, `AttackEffectManager`, `ProjectileEffect`: chọn nòng pháo thật của đúng phe,
  đổi world position sang board-local; tăng bán kính viên đạn từ .055 lên .105, đuôi đạn lên .060.
  Bay theo đường cong, có flash ở nguồn; thời gian hiệu ứng 2.10 giây, impact ở 1.00 giây.
  Board fixture không có biển mới dùng nguồn dự phòng; scene production và kiểm tra render đều dùng tàu biển thật.
- `game/GameController`: truyền phe bắn từ MatchSnapshot/Side. Sửa lỗi visual cũ coi mọi UUID PvP
  là phe tối vì so attackerId trực tiếp với chuỗi `player`. Hit/miss vẫn lấy từ kết quả hiện có, không tự tính ở renderer.
- `effects/HitEffect`: flash, cụm lửa cam, tia lửa, khói và ripple; nổ cao hơn mặt tàu để không bị thân tàu che.
  `MissEffect` chỉ có cột nước, giọt nước và ripple, không tạo lửa/khói nổ.
- `scene/board/BoardBuilder`: cạnh trên/phải xoay nhãn 180 độ, dưới/trái giữ hướng ban đầu.
  Cả 40 nhãn vẫn nằm đúng band và giá trị A–J/1–10 theo ô cũ; không đảo mapping tọa độ/raycast.

## Kiểm tra đã chạy

- `gradlew build :frontend:checkDesignerRendering :frontend:checkCombatPresentation`: thành công.
  Render cả **192 model**; kiểm tra mesh hữu hạn/nằm trong footprint và mọi catalog hình đều vẽ được.
  Vẽ/xóa/kéo/xoay tàu trên scene thật và Ready với phòng 3 tàu đều đạt.
- Kiểm tra 40 nhãn, đúng 20 nhãn đảo chiều, giữ nguyên định danh ô; 300 raycast của bài kiểm tra cũ đạt.
- Kiểm tra cả hai phe với ID người bắn dạng UUID: viên đạn bắt đầu tại world position của nòng pháo
  thật đúng phe, ngoài grid, sau khi chuyển qua rotation/scale của board. Kích thước shell mới được kiểm tra.
- `checkCombatPresentation`: render hai góc nhìn đối thủ, projectile, hit explosion và miss splash;
  kiểm tra impact/complete gọi đúng một lần và dọn hiệu ứng xong.
- `gradlew :frontend:checkCombatPresentation :frontend:checkGameplayRendering`: thành công.
  Fixture gameplay 3D chơi hết một trận, hit/miss/sunk/game over/restart, lặp ô và khóa thao tác khi đang animation đạt.
  Đây là fixture nội bộ, không đưa Play Local trở lại menu.
- Đã xem ảnh render hai phe và hiệu ứng. Log: `build/naval-visual-verification.log`, `build/naval-gameplay-check.log`.
  Ảnh: `build/combat-review/`, catalog: `build/designer-review/`, ảnh fixture gameplay: `build/gameplay-review/`.
  Đổi output mặc định của fixture khỏi `docs` để các lần kiểm tra không ghi đè ảnh baseline đã commit.

Chưa kiểm tra trận PvP bằng hai cửa sổ 3D hoặc macOS trong lượt này. Không thay protocol, backend,
luật bắn, background, castle, ocean material, camera hay minimap. Chỉ sửa scene objects cần cho bốn yêu cầu visual.
