# Super TV Cast — kế hoạch triển khai super app TV

Ngày nghiên cứu: 08/10/2026. Package giữ `com.tuntech.supertvstreamcast`; tên sản phẩm làm việc: **Super TV Cast**.

## Mục tiêu và phạm vi
Một binary Android/iOS: TV Remote + screen sharing + IPTV. Chọn hãng TV khi onboarding; cùng hệ thống tính năng, khác hướng dẫn và adapter kết nối. Không tạo nhiều app trùng nhau theo hãng. Store listing và chiến dịch quảng cáo chia theo intent/hãng; mỗi nhóm chỉ quảng bá khả năng đã nghiệm thu trên thiết bị.

Đây là roadmap theo cổng nghiệm thu, không phải cam kết “mọi TV đều hỗ trợ”. Có thể hoàn thành UI/logic trước, nhưng tương thích firmware và kết nối thật phải được xác minh riêng. Không giả số rating, review, số kênh hoặc kết nối thành công.

## Tham khảo thị trường
Các app có quy mô đáng kể hoặc sản phẩm chuyên biệt để học hỏi; không khẳng định đây là bảng xếp hạng doanh thu theo thời gian thực. Chỉ khảo sát store listing, chưa kiểm thử trực tiếp từng app đối thủ. Số lượt tải là snapshot từ trang store truy cập ngày nghiên cứu.

| Tham khảo | Bằng chứng | Học gì / áp dụng |
|---|---|---|
| [Lean Remote](https://play.google.com/store/apps/details?id=co.leanremote.universalremotecontrol.remotecontrol) | Trang Play hiển thị 10M+ downloads; smart TV và IR | Brand-first setup; phân biệt Wi-Fi và IR; điều khiển nằm trong vùng ngón tay |
| [Web Video Cast](https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo) | Trang Play hiển thị 50M+ downloads; web/local video, photos, audio | Trạng thái receiver chung; giải thích cast nội dung so với chiếu toàn màn hình |
| [TiviMate](https://play.google.com/store/apps/details?id=ar.tvplayer.tv) | IPTV player dành cho Android TV | Playlist người dùng, phân nhóm, favorites, lịch chương trình trong giai đoạn mở rộng |
| [UHF](https://apps.apple.com/us/app/uhf-love-your-iptv/id6443751726) | Sản phẩm IPTV trên hệ sinh thái Apple | Trải nghiệm thư viện và player nhất quán giữa thiết bị |

Không sao chép brand asset hay giao diện đối thủ. Artwork và icon của Super TV Cast được tạo riêng.

## Quy tắc kế thừa từ PetTranslator
Nguồn: `../PetTranslator/CLAUDE.md`.

- UI → domain → data; commonMain không gọi API Android/iOS trực tiếp.
- Screen lấy `StateFlow` từ ViewModel; composable con nhận state/callback.
- Logic có tests ở commonTest. KMP mới dùng `testAndroidHostTest` vì module hiện tại dùng plugin Android Multiplatform mới; không sao chép lệnh `:composeApp` từ dự án khác.
- Externalize strings; dùng design token; icon vector trước, art raster WebP; không bake chữ vào ảnh.
- Không sửa hoặc chép file từ các thư viện tuntech. Tích hợp cả module với package gốc khi đến giai đoạn monetization.
- Tài liệu chức năng đã triển khai ở `docs/context`; kế hoạch chưa hoàn thành ở `docs/plans`.
- Không sao chép app ID, Firebase, ad unit hay khóa của PetTranslator/ElectronicsRepair.
- ✅ 11/10/2026: đã có chuỗi cho 15 ngôn ngữ (en, ar, de, es, fr, pt, id/in, fil/tl, hi, th, vi, zh, ko, ja, tr). Còn lại trước khi phát hành quốc tế: người bản ngữ rà soát bản dịch và kiểm tra RTL.

## Flow tương tự ElectronicsRepair

ElectronicsRepair: Splash → onboarding theo lựa chọn người dùng → introduction ad → paywall → Home; lần sau Splash → Home → startup paywall nếu cấu hình cho phép; xin permission tại thời điểm sử dụng. Tham khảo trực tiếp `OnboardingViewModel.kt`, `AppFlowEffect.kt` và `CLAUDE.md` của dự án.

Super TV Cast:

1. App shell/load preferences (không delay splash giả).
2. Trang giới thiệu artwork + giá trị sản phẩm.
3. Chọn hãng: Samsung, LG, Sony BRAVIA, Google/Android TV, TCL, Hisense, Roku, TV khác.
4. Chọn nhu cầu: Remote / Mirror / IPTV.
5. Khi hoàn tất, lưu hãng/nhu cầu trên thiết bị và mở tab đã chọn; lần sau vào Home.
6. Kết nối/permission tại điểm dùng. Remote chưa kết nối phải disabled. IPTV không yêu cầu kết nối TV để xem trên điện thoại.
7. Giai đoạn monetization: introduction ad → first-start paywall có nút đóng → Home; kiểm tra entitlement trước startup paywall; Pro không quảng cáo; không interstitial khi chuyển tab hoặc mỗi lần bấm remote.

Paywall chỉ liệt kê product thật từ Qonversion; khi chưa có project key thì không tự mở paywall.

## Thiết kế — Midnight Cinema

- Nền navy `#07111F`, surface `#122135`, raised `#1B2E45`.
- Cyan `#67E5EF` cho hành động; coral `#FFAE9B` cho điểm nhấn/favorites.
- Typography Material 3; bo góc 18–24 dp; vùng chạm tối thiểu 48 dp; CTA 56 dp.
- Hero TV + điện thoại + remote, không chữ; WebP trong Compose Resources.
- Icon launcher: TV cyan, play coral; có SVG gốc, adaptive Android và PNG iOS.
- Icon điều hướng vẽ vector bằng Compose Canvas, cùng nét và palette.
- Home ưu tiên trạng thái kết nối rồi 3 feature cards. Remote tối giản, IPTV lazy list + search/favorites, mirror hướng dẫn cụ thể theo nền tảng.

## Các giai đoạn và cổng nghiệm thu

### GĐ1 — Nền tảng và design (đã triển khai code)
- KMP giữ cấu trúc :androidApp + :shared + iosApp hiện có.
- Design token, hero, launcher, onboarding có persistence, tab shell, settings.
- ViewModel StateFlow, repository/platform seams; EN/VI externalized.
- Gate: Android APK build; iOS common/platform compile; test logic; kiểm tra màn hình nhỏ, font lớn và accessibility trên thiết bị.

### GĐ2 — Vertical slice dùng thật (đã triển khai code, cần nghiệm thu TV thật)
- Sony BRAVIA IP + PSK: chỉ địa chỉ RFC1918, discovery IRCC codes từ TV, gửi lệnh SOAP; timeout, lỗi xác thực/kết nối, disconnect.
- M3U nội dung/URL HTTP(S): đọc dạng stream, giới hạn 64 MB/50.000 kênh (cắt bớt, không báo lỗi), loại scheme không hỗ trợ, dedupe, group, search, favorites.
- Android 17 LAN permission declared/requested at point of use, localized denial and back handling. See [local network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- Native player: Android Media3 ExoPlayer + PlayerView (HLS/TS, từ 09/10/2026); iOS AVPlayerViewController. Dừng/pause theo lifecycle.
- Screen sharing: mở Android Cast settings; iOS hướng dẫn Control Center/AirPlay. Đây là tích hợp luồng hệ thống, chưa phải streaming engine riêng.
- Gate: BRAVIA model/firmware thật, PSK đúng/sai, mất Wi-Fi, TV sleep; playlist thật HLS/live; Android Cast receiver/AirPlay thật; codec và lỗi playback.

### GĐ3 — Remote đa hãng (đã triển khai code cho Sony/Samsung/LG + quét LAN; cần nghiệm thu TV thật)
- ✅ Interface RemoteAdapter + `createAdapter`; Sony, Samsung Tizen, LG webOS adapters; capability từ phản hồi TV. Xem `docs/context/remote-discovery-iptv.md`.
- ✅ Quét /24 unicast theo thao tác người dùng (Samsung API, Sony interface info, LG SSAP). ✅ 09/10/2026: Wake-on-LAN (địa chỉ MAC do TV báo về) và lưu token ghép nối bằng Keystore/Keychain kèm nút "Quên các TV đã ghép nối". ✅ SSDP + DNS-SD (NSD/Bonjour) khi quét. Còn lại: entitlement multicast của Apple để gửi Wake-on-LAN trên iPhone thật.
- Samsung Tizen: ✅ pairing chấp thuận trên TV, token lưu mã hóa trên máy, WSS pin chứng chỉ theo host (TOFU), Wake-on-LAN qua `wifiMac`. Còn: test model 2016–2026.
- LG webOS: ✅ SSAP register (PROMPT, manifest không chữ ký), pointer socket, IME, launch points. Cần xác minh firmware 2025/2026 có chấp nhận manifest không ký; nếu không, cân nhắc module được duy trì.
- Android/Google TV: ✅ 09/10/2026 Remote v2 (chứng chỉ client trong Keystore/Keychain, ghép nối bằng mã 6 ký tự trên TV, pin chứng chỉ TV, phím bấm). ✅ 11/10/2026: mở app bằng app link (12 shortcut) và tìm TV qua DNS-SD `_androidtvremote2._tcp`. Còn: nhập chữ (IME), giữ phím, nghiệm thu trên TV thật. Cast không thay thế giao thức điều khiển TV.
- ✅ 11/10/2026 (code, chưa test TV thật): TCL/Hisense/TV khác tự nhận diện hệ điều hành khi kết nối (Roku → VIDAA → Google TV), không chọn giao thức theo nhãn hãng; TV chọn từ danh sách quét dùng đúng giao thức đã trả lời. Xem `docs/context/remote-discovery-iptv.md`.
- ✅ 11/10/2026 (code): Hisense VIDAA qua MQTT/TLS :36669, ghép nối mã 4 số. Rủi ro: giao thức không công khai; firmware mới đòi chứng chỉ client của Hisense sẽ từ chối (không nhúng chứng chỉ/khóa của Hisense). Gate: ≥2 model VIDAA (2020 và 2023+), mã đúng/sai, TV tắt màn hình.
- Discovery: Android NSD/SSDP, iOS Bonjour/SSDP phù hợp entitlement; manual IP fallback; chỉ scan LAN sau thao tác người dùng.
- Roku: ✅ 11/10/2026 (code, chưa test TV thật) adapter ECP (phím, nhập chữ `Lit_`, danh sách/mở kênh, phát hiện "Control by mobile apps" bị tắt, Wake-on-LAN). ⚠️ [Tài liệu ECP](https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md) ghi "ECP commands may not be sent from 3rd-party platforms (for example, mobile applications)". Cần quyết định pháp lý/sản phẩm trước khi phát hành (ví dụ tắt qua Remote Config ở thị trường nhạy cảm, không quảng bá từ khóa Roku). Gate: Roku TV + Roku stick, OS 14.1+ bật/tắt Control by mobile apps.
- ✅ 11/10/2026 (code, chưa test TV thật): thêm 3 nền tảng — Amazon Fire TV (DIAL + HTTPS :8080, mã PIN 4 số, phím/media/nhập chữ/mở app), Vizio SmartCast (HTTPS :7345/:9000, PIN 4 số, phím có xác nhận), Panasonic Viera (SOAP :55000, mọi phím kể cả số; model 2019+ cần phiên mã hóa → báo "chưa hỗ trợ"). Thêm hãng: Amazon, Vizio, Philips, Panasonic, Xiaomi, Toshiba, Sharp, Insignia, JVC, onn., Westinghouse, Skyworth/Coocaa, Haier, Casper, Nvidia Shield (tự nhận diện nền tảng). Quét LAN nhận Panasonic và Fire TV. ⚠️ Fire TV/Vizio theo tài liệu cộng đồng, không phải tài liệu hãng — cùng quyết định pháp lý như Roku. Còn: mã hóa Panasonic 2019+ (AES/HMAC), Philips JointSpace (TV không phải Android), Vizio danh sách app, Fire TV âm lượng (không có trong giao thức). Gate: Fire TV Stick + Fire TV Edition TV, Vizio 2018+ (cổng 7345) và đời cũ (9000), Panasonic trước/sau 2019.
- Gate: matrix ≥2 model/firmware cho mỗi adapter, permission denied, reconnect, token revoked, TV asleep; trạng thái capability xác thực trước enable nút.

### GĐ4 — Casting/mirroring nâng cao + IPTV library (chưa triển khai)
- ✅ 11/10/2026 (code, chưa test TV thật): tab Cast — truyền ảnh (trình chiếu), video, nhạc, link video tới TV DLNA/UPnP (Android + iOS) và Chromecast (Android), server nội bộ có Range, trình điều khiển (seek, ±10 s, trước/sau, âm lượng TV, dừng). Xem `docs/context/cast.md`. Còn lại: nghiệm thu Samsung/LG/Sony/Panasonic/Philips + Chromecast thật; AirPlay route picker cho video/nhạc trên iOS; chuyển HEIC→JPEG; foreground service Android/background iOS khi phát file; thanh mini nổi trên dock ở mọi tab; entitlement multicast iOS; người bản ngữ duyệt chuỗi `strings_cast.xml`.
- Google Cast SDK cho media URL; DLNA receiver tương thích; AirPlay route picker iOS.
- Mirroring Android thực sự: MediaProjection consent mỗi session → foreground service → encoder → transport → receiver companion. Không dùng Default Media Receiver như một encoder/mirroring engine có sẵn.
- iOS: ReplayKit broadcast extension + receiver riêng nếu cần streaming trong app; AirPlay hệ thống vẫn là đường ưu tiên.
- Theo [Android MediaProjection](https://developer.android.com/media/grow/media-projection), áp dụng permission/service và session lifecycle phù hợp SDK mục tiêu.
- Persist playlist/favorites vào storage phù hợp; hiện library chỉ tồn tại trong session để tránh lưu token stream vào prefs không bảo vệ.
- ✅ 09/10/2026 (code, chưa chạy trên thiết bị): thư viện IPTV lưu trên máy với nhiều nguồn, Xtream phim/series, xem tiếp, yêu thích, kênh ẩn + mật mã, lịch chương trình theo kênh, liên kết chia sẻ `supertvcast://`. Xem `docs/context/iptv-library.md`.
- ✅ Nguồn IPTV: URL playlist (HTTP/HTTPS), Xtream Codes (live), tệp trên thiết bị, phát một luồng, EPG XMLTV now/next + hướng dẫn sử dụng. Xem `docs/context/iptv-sources.md`.
- ✅ 09/10/2026: parser M3U chịu lỗi (logo, user-agent/referer), tải dạng stream + theo redirect, hủy/tiến độ nhập, EPG `.gz`, player Media3, logo kênh.
- ✅ 09/10/2026 (code, chưa chạy trên thiết bị): tệp thư viện được mã hóa bằng khóa Keystore/Keychain; lịch phát sóng dạng lưới/danh sách + nhắc lịch; mini player, PiP và Chromecast (Android), lấp đầy màn hình; QR chia sẻ/quét; giới hạn bản miễn phí qua Remote Config `IPTV_SETTINGS` (mặc định tắt); test `IptvViewModel`.
- Còn lại IPTV (so với `../iptv_kmp`): nghiệm thu toàn bộ trên thiết bị; Xtream catch-up; tab nguồn EPG riêng và lịch quá 36 giờ; menu audio/phụ đề/tốc độ riêng của app; iOS phát `.ts` thô (cần engine ngoài AVPlayer); đồng bộ nguồn sang TV (cần backend); chốt con số giới hạn miễn phí khi có Qonversion thật. Chi tiết ở cuối `docs/context/iptv-library.md`.
- Media3 Android, mở rộng AVPlayer iOS, multiple playlists, playback controls, subtitles/track selection.
- Gate IPTV nguồn mới: tài khoản Xtream thật (đúng/sai/hết hạn, panel HTTP và HTTPS), XMLTV thật cỡ lớn (thời gian parse/bộ nhớ trên máy yếu), document picker Android/iOS (Drive/iCloud), luồng HLS/TS thật.
- Gate: receiver thật, 30 phút live, screen off/revoke/rotation, latency/fps/memory đo được; không chiếu DRM protected content; link có token không lọt analytics.

### GĐ5 — Monetization, ASO và release (flow vào app đã triển khai code; chưa có ID thật)
- ✅ Submodules tuntech (`common`, `monetization`, `mmp`, `mmp_firebase`) và flow Splash → Onboarding → Paywall → Dashboard theo cấu trúc Smart Printer / Cam Scanner. Xem `docs/context/entry-flow.md`.
- Còn lại: tạo Firebase project, Qonversion project + products, AdMob app/ad units riêng và thay các giá trị giữ chỗ; đẩy Remote Config lên console; URL điều khoản/quyền riêng tư; nghiệm thu UMP consent, mua/khôi phục, app-open ad trên thiết bị thật. (09/10/2026: iOS đã build bằng Xcode và mở được trên simulator.)
- ✅ 11/10/2026 (code, chưa chạy trên thiết bị): Settings thêm nhóm Chung/Hỗ trợ/Pháp lý/Khắc phục sự cố theo AirPrint + RetroOne (xem `docs/context/ui-design.md`). `SUPPORT_EMAIL`, `TERM_OF_SERVICE_URL`, `PRIVACY_POLICY_URL` đã có mặc định trong app (`contact@mmtstudio.site`, `mmtstudio.site/terms`, `/privacy`); còn lại: kiểm tra đánh giá/chia sẻ/gửi log/khôi phục trên máy thật.
- ✅ 11/10/2026 (code, chưa chạy trên thiết bị): màn chọn ngôn ngữ theo RetroOne — lần đầu mở app (Splash → Language → Onboarding) và từ Cài đặt → Ngôn ngữ (thay dialog cũ). Còn lại: kiểm tra trên máy thật Android (activity recreate giữ back stack) và iOS (chuỗi đổi ngay sau khi áp dụng); thêm khóa `LANGUAGE` vào `ADS_NATIVE_SETTINGS` trên Firebase console; đã có đủ 15 ngôn ngữ như RetroOne (bản dịch do AI soạn, cần người bản ngữ rà soát, nhất là tiếng Ả Rập/Hindi/Thái và văn bản pháp lý); kiểm tra bố cục RTL tiếng Ả Rập và chuỗi dài (tiếng Đức) trên máy thật.
- ✅ 11/10/2026: App Store Connect đã điền metadata 9 ngôn ngữ (en-US, vi, es-MX, pt-BR, fr-FR, de-DE, ja, ko, zh-Hans), danh mục, độ tuổi, giá Free, phân phối (trừ Trung Quốc đại lục), thông tin review, bản địa hoá IAP; bộ 7 screenshot iPhone 6.9" + iPad 13" mỗi ngôn ngữ (UI thật + nền GPT). Xem `docs/context/store-listing.md`. Còn lại trên web ASC: App Privacy, câu hỏi content rights, ảnh review cho 3 IAP, upload build + chọn build; người bản ngữ rà soát mô tả store.
- ✅ 11/10/2026: iPad — trang, dock, onboarding, chọn ngôn ngữ giới hạn 720 dp và căn giữa (trước đây kéo giãn layout điện thoại).
- Chưa thêm: `mmp_facebook`, `liquid_glass`, `ai`, push notification, App Check, build type staging, R8/release signing.
- Free: kết nối và remote cơ bản. Pro: trải nghiệm không ads, library nâng cao, casting nâng cao sau khi hỗ trợ thật. Giới hạn cụ thể phải được xác định bằng test giá và entitlement thật.
- Analytics: onboarding → connect_attempt → connect_success → feature_used → paywall → purchase. Không log IP, PSK, playlist URL/token.
- ✅ 11/10/2026 (code, chưa kiểm tra trên Firebase DebugView): mọi lượt bấm log `ec_<khu>_<nút>` qua `analytics/ClickEvent.kt`, danh sách ở `docs/context/analytics-events.md`. ✅ 11/10/2026: compile/link iOS đã qua. ✅ Event kết quả `tv_connect_attempt/_code_required/_success/_fail` và `feature_used` (remote; IPTV khi player native thật sự phát) trong `analytics/AppEvent.kt`, có test. Còn lại: xác nhận event trên DebugView Android/iOS, đăng ký custom dimension (`tab`, `brand`, `screen`, `source`, `product`, `reason`, `feature`, …) trên Firebase/GA4; `feature_used` cho Mirror (chỉ mở cài đặt Cast hệ thống nên chưa biết kết quả).
- Rà soát bản dịch 15 ngôn ngữ, RTL, VoiceOver/TalkBack, dynamic type, privacy/data safety, license audit, release signing, TestFlight/internal testing.
- Store: một app, custom listings/product pages/ads theo hãng; chỉ mở group từ khóa tương ứng adapter đã qua gate.

## Ma trận hiện tại

| Khả năng | Android | iOS | Giới hạn hiện tại |
|---|---|---|---|
| Onboarding, Home, chọn hãng | Implemented | Implemented | EN/VI |
| Flow Splash/Paywall/ads (tuntech modules) | Implemented | Implemented (build Xcode + mở trên simulator) | Firebase/Qonversion/AdMob đang là giá trị giữ chỗ; chưa test mua hàng/quảng cáo thật |
| Sony BRAVIA Remote | Implemented | Implemented | Manual IP + PSK; model hỗ trợ IP control; chưa test TV thật |
| Samsung Tizen / LG webOS remote | Implemented | Implemented | Pairing trên TV; token lưu mã hóa (Keystore/Keychain); chưa test TV thật |
| Google TV / Android TV remote | Implemented | Implemented | Tìm qua DNS-SD hoặc nhập IP + mã trên TV; phím bấm + shortcut mở app (chưa nhập chữ); chưa test TV thật |
| Wake-on-LAN (Samsung/LG/Sony) | Implemented | Implemented (thiết bị thật cần entitlement multicast) | Chỉ khi TV đã báo địa chỉ MAC ở lần kết nối trước; không xác nhận được TV đã bật |
| Roku remote (ECP) | Implemented | Implemented | Chưa test TV thật; chính sách Roku về app di động bên thứ ba cần quyết định trước release |
| Hisense VIDAA remote | Implemented | Implemented | Giao thức không chính thức; firmware đòi chứng chỉ Hisense sẽ từ chối; chưa test TV thật |
| Amazon Fire TV remote | Implemented | Implemented | PIN 4 số; giao thức không chính thức; không có âm lượng/kênh; chưa test TV thật |
| Vizio SmartCast remote | Implemented | Implemented | PIN 4 số; không nhập chữ/app; chưa test TV thật |
| Panasonic Viera remote | Implemented | Implemented | Model không mã hóa; model 2019+ báo chưa hỗ trợ; chưa test TV thật |
| TCL / Hisense / Panasonic / Toshiba / Philips / … / TV khác | Implemented (tự nhận diện) | Implemented (tự nhận diện) | Thứ tự: thăm dò im lặng (Roku, VIDAA, Viera) → Fire TV/Vizio → Google TV cuối |
| Touchpad, bàn phím số, nhập chữ, app TV | Implemented | Implemented | Theo capability từng adapter |
| Screen sharing hệ thống | Cast settings | AirPlay hướng dẫn | Receiver/system quyết định hỗ trợ; không báo mirroring active |
| IPTV M3U import và native player | Implemented | Implemented | Nhiều nguồn lưu trên máy, nhóm, xem tiếp, yêu thích, kênh ẩn; chưa chạy thử trên thiết bị sau khi đổi sang thư viện lưu trữ |
| IPTV Xtream Codes, tệp thiết bị, luồng đơn, EPG XMLTV | Implemented | Implemented | Xtream live + phim + series; EPG `.xml`/`.xml.gz` (không lưu, tải lại mỗi phiên); iOS không phát `.ts` thô; chưa test với nhà cung cấp thật |
| Cast IPTV sang TV | Implemented (Chromecast, Default Media Receiver) | AirPlay của AVKit | Chưa thử với receiver thật; header riêng của kênh không tới được receiver |
| Quét LAN theo yêu cầu | Implemented | Implemented | /24 unicast + SSDP + DNS-SD; iPhone thật cần entitlement multicast cho SSDP |
| Secure pairing store | Implemented | Implemented | Token Samsung/LG, pin chứng chỉ Google TV, MAC; PSK Sony và đăng nhập Xtream vẫn chỉ trong phiên |

## Cách làm từng bước tiếp theo

1. ✅ 09/10/2026: build Android, 50 test host, compile/link iOS và build Xcode simulator đều qua (xem `docs/context/validation.md`). Còn lại: đi hết flow iOS trên simulator/thiết bị thật.
2. Nghiệm thu Samsung/LG/Sony trên TV thật theo gate GĐ3; nếu LG từ chối manifest không ký thì đổi chiến lược trước khi quảng bá.
3. ✅ 09/10/2026 (code): Google TV Remote v2 (TLS client cert + pairing code), Wake-on-LAN, lưu token bằng Keystore/Keychain — xem `docs/context/remote-discovery-iptv.md`; cần nghiệm thu trên TV thật. Kế tiếp media casting trước full mirroring để giảm rủi ro receiver/codec. Không chạy ads acquisition cho hãng chưa nghiệm thu.

## Submit Google Play (việc còn lại, 11/10/2026)

1. Play Console: tạo app `com.tuntech.supertvstreamcast`, upload `androidApp-release.aab` lên Internal testing (bật Play App Signing).
2. Tạo sản phẩm: subscription monthly (base plan `monthly`, offer dùng thử 3 ngày) / yearly (base plan `yearly`), in-app `lifetime` — id như `docs/context/store-listing.md`.
3. Qonversion → Google Play: tạo service account có quyền Financial data + Manage orders, upload JSON key, bật RTDN (Pub/Sub).
4. Store listing từ `store-assets/google-play/listing.json`; screenshot phone Android (chưa có — ảnh iOS có khung iPhone và màn AirPlay, không dùng được).
5. App content: Data safety (AdMob, Firebase Analytics/Crashlytics, Qonversion: device/app IDs, diagnostics, purchase history; không thu credential/playlist), Ads = có, Content rating, Target audience 18+, quyền Camera/Notification/Local network.
6. Kiểm thử thật bản release (mua thử bằng license tester) trước khi đẩy Production.

## Monetization follow-up (11/10/2026)

- ✅ 11/10/2026: Remote Config template published with `ADS_APP_OPEN_SETTINGS`, `ADS_INTERSTITIAL_SETTINGS`, `ADS_REWARDED_SETTINGS`, `ADS_NATIVE_SETTINGS` (incl. `LANGUAGE`, `ONBOARDING_FULL_1/2`), `IPTV_SETTINGS` (1 source, 1800 s) and new `PREMIUM_SETTINGS`, equal to `remote_config_defaults.json`. `SUPPORT_EMAIL` is still only an in-app default.
- [ ] Verify on devices with real ad units: splash app-open timing, onboarding ad page with a filled and an empty ad, rewarded grant only after a full view, app-open not shown after pickers/share/casting.
- [ ] A/B test the free limits (remote lock list, IPTV 1 source / 30 min) against retention and conversion before tightening further; native-speaker review of the new gate strings.

