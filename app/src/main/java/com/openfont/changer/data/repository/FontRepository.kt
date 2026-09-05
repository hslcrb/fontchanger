package com.openfont.changer.data.repository

import com.openfont.changer.data.model.PresetFont

class FontRepository {

    fun getPopularPresetFonts(): List<PresetFont> {
        return listOf(
            PresetFont(
                id = "pretendard",
                name = "Pretendard",
                designer = "길형진 (orioncactus)",
                license = "SIL Open Font License",
                previewText = "다람쥐 헌 쳇바퀴에 타고파. The quick brown fox jumps over the lazy dog.",
                downloadUrl = "https://github.com/orioncactus/pretendard/releases/latest",
                tags = listOf("UI 최적화", "본고딕 기반", "인기 1위", "가독성")
            ),
            PresetFont(
                id = "d2coding",
                name = "D2Coding",
                designer = "NAVER",
                license = "OFL-1.1",
                previewText = "val code = font.apply(); // 0123456789 {} () [] <>",
                downloadUrl = "https://github.com/naver/d2codingfont/releases/latest",
                tags = listOf("코딩용", "고정폭(Monospace)", "개발자 추천")
            ),
            PresetFont(
                id = "maruburi",
                name = "마루 부리 (Maru Buri)",
                designer = "NAVER Clova & 안그라픽스",
                license = "OFL-1.1",
                previewText = "한글의 온화한 곡선과 단정한 부리(Serif)를 품은 글꼴",
                downloadUrl = "https://github.com/naver/maru-buri/releases/latest",
                tags = listOf("명조체", "세리프", "단아함", "문학")
            ),
            PresetFont(
                id = "gmarketsans",
                name = "Gmarket Sans",
                designer = "Gmarket",
                license = "무료 상용 가능",
                previewText = "쇼핑을 바꾸는 쇼핑, 깔끔하고 트렌디한 타이틀 폰트",
                downloadUrl = "http://corners.gmarket.co.kr/company/ko/brand",
                tags = listOf("제목용", "트렌디", "깔끔함")
            ),
            PresetFont(
                id = "jetbrains_mono",
                name = "JetBrains Mono",
                designer = "JetBrains",
                license = "OFL-1.1",
                previewText = "fn main() -> Result<(), Error> { println!(\"FontCraft!\"); }",
                downloadUrl = "https://github.com/JetBrains/JetBrainsMono/releases/latest",
                tags = listOf("영어 폰트", "리게이쳐", "IDE 기본")
            ),
            PresetFont(
                id = "tossface",
                name = "Toss Face Emoji",
                designer = "Viva Republica (Toss)",
                license = "SIL OFL 1.1",
                previewText = "대한민국 감성이 듬뿍 담긴 새로운 시스템 이모지 폰트 ✨🚀🍕🔥",
                downloadUrl = "https://toss.im/tossface",
                tags = listOf("이모지 교체", "시스템 컬러 이모지")
            )
        )
    }
}
