import { reviewerSummary, STAGE_LABEL } from '../versioning'
import type { Stage } from '../versioning'

/**
 * 버전의 현재 단계 표식 하나. 작업중 / 검토대기 / 검토중 / 발송대기 / 발송이 같은 모양(높이·모서리·여백·글자 크기)이고
 * 단계마다 색(--stage-*)이 다르다. 목록 카드 머리, 파일 사슬의 현재 파일 행, 개요·전체 문서 행에서 같은 부품을 쓴다.
 * rejected면 검토에서 거절된 채 작업중으로 돌아왔다는 뜻으로, 표식 옆에 글자 '거절됨'이 붙는다(새 표식 모양은 없다).
 * worker가 있고 단계가 작업중이면 글자가 '작업중 · 이름'이 된다(누가 작업 중인지). 길면 한 줄 말줄임이고 전체는 title로 본다.
 * reviewers가 있고 단계가 검토중이면 '검토중 · 첫 이름[ 외 n명]'이 되고 title에는 전체 이름이 들어간다.
 */
export function StageBadge({
  stage,
  rejected = false,
  worker,
  reviewers = [],
}: {
  stage: Stage
  rejected?: boolean
  worker?: string
  reviewers?: string[]
}) {
  const reviewing = stage === 'reviewing' && reviewers.length > 0
  const text =
    stage === 'working' && worker
      ? `${STAGE_LABEL.working} · ${worker}`
      : reviewing
        ? `${STAGE_LABEL.reviewing} · ${reviewerSummary(reviewers, 1)}`
        : STAGE_LABEL[stage]
  const title = reviewing ? `${STAGE_LABEL.reviewing} · ${reviewers.join(', ')}` : text !== STAGE_LABEL[stage] ? text : undefined
  return (
    <>
      <span className={`stage-badge ${stage}`} title={title}>
        {text}
      </span>
      {rejected && <span className="rejected-mark">거절됨</span>}
    </>
  )
}
