import { useState } from 'react'
import { useData } from '../store/useData'
import { DOC_FILE_ACCEPT, docFileError } from '../versioning'
import CategoryDialog from './CategoryDialog'
import { Dropzone } from './files'
import { IconPlus, IconUpload } from './icons'

interface Props {
  onCreate: (files?: File[]) => void
}

/** 저장된 문서가 하나도 없을 때의 상세 영역: 시작하기 */
export default function Overview({ onCreate }: Props) {
  const { categories } = useData()
  const [creatingCategory, setCreatingCategory] = useState(false)
  const [error, setError] = useState('')

  const steps = [
    { label: '카테고리 만들기', note: '계약서, 회의록처럼 문서를 묶을 이름', done: categories.length > 0 },
    { label: '첫 문서 올리기', note: '파일을 올리면 첫 작업 버전이 됩니다', done: false },
    { label: '발송 기록 남기기', note: '보낸 날짜와 받는 곳을 적으면 그 버전이 고정됩니다', done: false },
  ]
  const doneCount = steps.filter((s) => s.done).length
  return (
    <section className="overview first-run" aria-labelledby="first-run-title">
      <h1 id="first-run-title">문서함을 준비하세요</h1>
      <p className="sub">
        세 단계 중 <span className="num">{doneCount}</span>단계를 마쳤습니다.
      </p>
      <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={3} aria-valuenow={doneCount} aria-label="준비 단계">
        <span style={{ width: `${(doneCount / 3) * 100}%` }} />
      </div>
      <ol className="steps">
        {steps.map((s, i) => (
          <li key={s.label} className={s.done ? 'done' : ''}>
            <div className="step-text">
              <strong>{s.label}</strong>
              <span>{s.done ? '완료' : s.note}</span>
            </div>
            {i === 0 && !s.done && (
              <button type="button" className="btn" onClick={() => setCreatingCategory(true)}>
                <IconPlus size={14} />
                카테고리 만들기
              </button>
            )}
          </li>
        ))}
      </ol>
      <Dropzone
        multiple={false}
        accept={DOC_FILE_ACCEPT}
        check={docFileError}
        onReject={setError}
        onFiles={(files) => {
          setError('')
          onCreate(files)
        }}
        className="dropzone first-drop"
      >
        <IconUpload className="dz-icon" size={20} />
        <p>
          <strong>첫 문서가 될 파일을 여기에 놓으세요</strong>
        </p>
        <p className="hint">한 번에 파일 하나만 올릴 수 있습니다(.hwpx·.pdf). 클릭해서 고를 수도 있습니다.</p>
      </Dropzone>
      {error && <p className="form-error">{error}</p>}
      {creatingCategory && <CategoryDialog onClose={() => setCreatingCategory(false)} />}
    </section>
  )
}
