import { STAGE_LABEL, STEPS, stageStep } from '../versioning'
import type { Stage } from '../versioning'
import { IconClose } from './icons'

type StepState = 'done' | 'current' | 'future'

/**
 * 진행 단계 5칸(작업중 → 검토대기 → 검토중 → 발송대기 → 발송)을 선으로 이은 표.
 * 지난 칸은 그 단계 색으로 채우고, 지금 칸은 채움 + 굵은 이름 + 고리, 앞으로 올 칸은 무채색 외곽선.
 * rejected면 검토중 칸에 거절 표시(빨간 고리와 X, 상세에서는 '거절됨' 글자)를 단다. 단계는 작업중이다.
 * compact면 라벨 없는 작은 점 다섯 개(목록 카드용).
 */
export function StageStepper({ stage, rejected = false, compact = false }: { stage: Stage; rejected?: boolean; compact?: boolean }) {
  const at = stageStep(stage)
  const stateOf = (i: number): StepState => (i < at ? 'done' : i === at ? 'current' : 'future')
  return (
    <ol className={`stepper ${compact ? 'compact' : ''}`} aria-label={`진행 단계: ${STAGE_LABEL[stage]}${rejected ? ', 검토에서 거절됨' : ''}`}>
      {STEPS.map((s, i) => {
        const mark = rejected && s.key === 'reviewing'
        return (
          <li
            key={s.key}
            className={`step s-${s.key} ${stateOf(i)} ${mark ? 'rejected' : ''}`}
            aria-current={stateOf(i) === 'current' ? 'step' : undefined}
          >
            <span className="step-node" aria-hidden="true">
              {mark && !compact && <IconClose size={10} />}
            </span>
            {!compact && <span className="step-label">{s.label}</span>}
            {mark && !compact && <span className="step-reject">거절됨</span>}
          </li>
        )
      })}
    </ol>
  )
}
