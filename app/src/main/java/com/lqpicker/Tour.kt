package com.lqpicker

/** Màn hình mà bước hướng dẫn cần hiển thị. */
enum class TourScreen { MAIN, OWNED }

/** Một bước hướng dẫn. key trùng với tên đánh dấu trong giao diện (tourTarget). */
data class TourStep(
    val key: String,
    val screen: TourScreen,
    val title: String,
    val body: String
)

object TourSteps {
    val all: List<TourStep> = listOf(
        TourStep(
            "welcome", TourScreen.MAIN,
            "Chào bạn!",
            "Trước tiên hãy chọn các tướng bạn đang sở hữu. Sau đó app sẽ hướng dẫn từng phần để bạn dùng nhanh nhất."
        ),
        TourStep(
            "owned_search", TourScreen.OWNED,
            "Tìm tướng",
            "Gõ tên tướng để tìm nhanh. Gõ không dấu cũng được."
        ),
        TourStep(
            "owned_lane", TourScreen.OWNED,
            "Lọc theo vị trí",
            "Chọn Caesar, Rừng, Giữa, Rồng hoặc Trợ thủ để xem riêng các tướng đánh được ở vị trí đó."
        ),
        TourStep(
            "owned_grid", TourScreen.OWNED,
            "Tướng của bạn",
            "Chạm vào tướng để bật hoặc tắt. Tướng sáng là tướng bạn có. App chỉ gợi ý trong số này."
        ),
        TourStep(
            "owned_bulk", TourScreen.OWNED,
            "Chọn nhanh",
            "\"Chọn hết\" bật tất cả, \"Bỏ hết\" tắt tất cả. Xong thì bấm ← Quay lại để tiếp tục."
        ),
        TourStep(
            "coach", TourScreen.MAIN,
            "Hướng dẫn",
            "Dòng ★ này nhắc bạn bước tiếp theo nên làm gì."
        ),
        TourStep(
            "lane", TourScreen.MAIN,
            "Vị trí của bạn",
            "Chọn vị trí trước khi vào trận. App sẽ chỉ gợi ý tướng đánh được ở vị trí này."
        ),
        TourStep(
            "team", TourScreen.MAIN,
            "Đội hình",
            "Ô \"Bạn\" là tướng của bạn. 4 ô xanh là đồng đội, 5 ô đỏ là đối thủ. Chạm ô để chọn tướng."
        ),
        TourStep(
            "bans", TourScreen.MAIN,
            "Tướng bị cấm",
            "Thêm tướng bị cấm để app không gợi ý nhầm. Chạm tên tướng để gỡ."
        ),
        TourStep(
            "scan", TourScreen.MAIN,
            "Quét màn hình",
            "Khi đang chọn tướng trong trận, bật quét để app tự đọc tên tướng trên màn hình. Bạn vẫn có thể nhập tay."
        ),
        TourStep(
            "suggest", TourScreen.MAIN,
            "Gợi ý",
            "Các tướng được xếp theo điểm. Mỗi gợi ý có lý do cộng và trừ để bạn hiểu vì sao."
        ),
        TourStep(
            "items", TourScreen.MAIN,
            "Trang bị",
            "Gợi ý loại trang bị phù hợp với đội hình và tướng của bạn."
        ),
        TourStep(
            "final", TourScreen.MAIN,
            "Xong rồi!",
            "Bấm nút \"?\" ở đầu trang bất cứ lúc nào để xem lại hướng dẫn."
        )
    )
}
