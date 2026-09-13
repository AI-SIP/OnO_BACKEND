# 꾸미기 API 골든 응답

서버가 실제로 뱉는 꾸미기 API 응답 본문을 그대로 굳혀 둔 파일들이다. 프론트와 백엔드가
같은 JSON 을 보게 하려고 만들었다.

## 왜 있는가

치장 API 를 프론트에 연동했는데 두 레포가 아직 한 번도 같은 JSON 을 본 적이 없었다.
프론트는 자기가 손으로 적은 더미 JSON 으로 파싱을 테스트하고 서버는 서버대로 자기 응답을
테스트하고 있어서, 필드 이름 하나가 어긋나도 양쪽 테스트는 그대로 초록으로 남는다.
그래서 서버가 실제로 만드는 본문을 파일로 굳혀 계약서로 쓰기로 했고, 프론트가 같은 파일을
`test/fixtures/` 에 두고 읽으면 서버 응답 모양이 바뀌는 순간 양쪽에서 한 번씩 깨진다.

`CommonResponse` 래퍼를 포함한 **응답 본문 전체**를 담는다. 프론트가 벗겨내야 하는 껍데기까지
계약의 일부라 `$.data` 만 잘라 두지 않았다. 성공 응답의 `CommonResponse` 는
`@JsonInclude(NON_NULL)` 이라 `errorCode` 와 `message` 가 아예 빠지고 `data` 하나만 남는다.

## 파일

| 파일 | 무엇 |
|---|---|
| `get-cosmetics.json` | `GET /api/cosmetics` 성공 응답 |
| `equip-all.json` | `PUT /api/cosmetics/equip-all` 성공 응답. 충돌이 없어 `unequippedSlots` 가 빈 배열이다 |
| `equip-all-conflict.json` | `PUT /api/cosmetics/equip-all` 중 충돌로 한 자리가 벗겨진 응답. `unequippedSlots` 에 `HEAD` 가 실린다 |

만드는 쪽은 `CosmeticGoldenResponseTest` 하나다. MockMvc 로 실제 직렬화를 거친 응답을 받아
그대로 쓰고, 같은 테스트가 다음 실행부터 이 파일과 응답을 비교한다.

## 키 순서를 어떻게 고정했나

골든이 실행마다 달라지면 계약서 노릇을 못 하기 때문에 **파일로 쓸 때 모든 객체의 키를 이름순으로
정렬**했다. 배열은 순서 자체가 의미(그리는 층 순서)를 갖기 때문에 손대지 않는다.

지금 구현만 보면 `equipped` 는 `EnumMap` 이고 `items` 와 `slots` 는 정렬된 리스트라 서버 쪽
순서가 이미 고정돼 있지만, 그건 구현이 우연히 그런 것이지 계약이 아니다
(`CosmeticItemResponseDto` 주석도 "JSON 필드 순서는 계약이 아니다" 라고 못박고 있다).
`Map` 을 쓰는 `equipped` 자리는 구현체가 바뀌면 순서도 같이 바뀌기 때문에 정렬해서 쓰는 편이
안전하다고 판단했다.

비교도 문자열이 아니라 JSON 트리로 한다. 들여쓰기나 키 순서 같은 의미 없는 차이로 깨지면
사람이 골든을 안 믿게 되기 때문에, `JsonNode#equals` 로 객체는 순서를 무시하고 배열은 순서까지
본다. 그래서 프론트가 이 파일을 자기 포맷터로 다시 찍어 두어도 계약 검증에는 영향이 없다.

## 어떤 아이템만 남겼나

카탈로그 전체는 63 개인데 통째로 실으면 파일이 수천 줄이 되고, 아이템 하나가 늘 때마다 골든이
흔들려서 정작 봐야 할 응답 구조의 변화가 묻힌다. 그래서 **프론트 파싱이 마주칠 모양이 한 번씩
들어가도록** 15 개만 남기고 나머지는 테스트 안에서 `active = 0` 으로 내렸다. 개구리 본체(`BASE`)
행은 `baseImageUrl` 이 읽기 때문에 그대로 둔다.

| 아이템 | 자리 | 남긴 이유 |
|---|---|---|
| `bg_spring` | BACKGROUND | 능력치로 열리는 것, `owned: true` |
| `bg_space` | BACKGROUND | 같은 자리에서 `owned: false` 인 것 |
| `outfit_cardigan` | OUTFIT | `fullBody: true` |
| `outfit_graduate` | OUTFIT | `fullBody: true` 이면서 `setId` 와 `setNameKo` 가 있는 것, 잠김 |
| `back_backpack_navy` | BAG | `layerOrder` 200 으로 자리 기본값(450)을 덮어쓰는 것 |
| `bag_mini_backpack` | BAG | 같은 자리에서 `layerOrder` 가 `null` 인 것 |
| `scarf` | NECK | 자리 채우기 |
| `glasses_round` | FACE | 자리 채우기 |
| `hat_beanie` | HEAD | `requiredAbility` 가 있는 것 |
| `headband_sprout` | HEAD | `requiredAbility` 가 `null` 인 것(총 학습 레벨로 열린다) |
| `hat_graduate` | HEAD | 세트에 속하면서 잠긴 것 |
| `prop_diploma` | HAND | 세트에 속하면서 잠긴 것 |
| `badge_leaf_star` | BADGE | `requiredAbility` 가 `null` 이면서 열린 것 |
| `effect_petals` | EFFECT | 자리 채우기 |
| `frame_spring` | FRAME | `composited: false` 인 자리의 아이템이고 혼자 SVG 경로다 |

열 자리를 모두 한 번씩 채우도록 골랐다. `slots` 배열은 카탈로그와 무관하게 `CosmeticSlot` 에서
바로 나오기 때문에 추리는 것과 상관없이 10 개가 그대로 실리고, `composited: false` 인 `FRAME`
도 여기 들어 있다.

골든을 찍는 사용자는 다섯 레벨이 모두 5 다. 다 자란 사용자로 찍으면 `owned: false` 가 한 건도
없어서 프론트가 잠긴 아이템을 어떻게 받는지 이 파일로는 알 수 없게 된다.

**지금 이 파일에 없는 모양이 하나 있다.** `conflictsWith` 가 전부 빈 배열인데, 시드에 충돌
조합이 하나도 들어 있지 않아서 그렇다. 값이 채워진 모양을 보고 싶어서 억지로 넣으면 운영
카탈로그와 다른 것을 계약서라고 두는 셈이라 그대로 뒀다. 카탈로그에 충돌이 실제로 들어가는 날
골든을 다시 찍으면 된다.

## 갱신하는 법

응답을 일부러 바꿨으면 아래로 다시 쓴다.

```bash
./gradlew test --tests "*CosmeticGoldenResponseTest" -Dcosmetic.golden.update=true
```

환경 변수도 같은 일을 한다.

```bash
COSMETIC_GOLDEN_UPDATE=true ./gradlew test --tests "*CosmeticGoldenResponseTest"
```

갱신 모드에서는 비교를 건너뛰고 파일을 덮어쓰기 때문에, **다시 쓴 뒤에는 반드시 `git diff` 로
무엇이 바뀌었는지 눈으로 확인하고 커밋한다.** 의도하지 않은 줄이 같이 바뀌어 있으면 응답이
생각보다 넓게 바뀐 것이다.

## 프론트가 같이 갱신해야 한다

프론트 레포(`OnO_FRONT`)가 이 파일들과 같은 내용을 `test/fixtures/` 에 두고 파싱 테스트에서
읽는다. 그래서 **서버 응답을 바꿔 여기 골든을 다시 썼으면 프론트 쪽 파일도 같은 내용으로
갱신해야 한다.** 한쪽만 고치면 다른 쪽 테스트가 옛 모양을 계속 통과시키기 때문에, 두 레포가
같은 JSON 을 본다는 이 파일의 전제가 그 순간 깨진다.
