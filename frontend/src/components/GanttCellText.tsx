interface Props {
  /** 글자가 놓일 칸(px) */
  cell: { left: number; width: number }
  todayX: number
  text: string
}

/**
 * 색칸 안의 글자. 칸에 들어가지 않으면 칸 오른쪽에 둔다(폭은 글자 수로 어림한다).
 * gantt-whole-text: 앞부분이 문서 열에 가려지면 GanttView가 통째로 숨긴다(잘린 날짜가 다른 날짜로 읽히지 않게)
 */
export default function GanttCellText({ cell, todayX, text }: Props) {
  const textW = text.length * 10 + 16
  const fits = textW <= cell.width
  // 글자가 놓일 수 있는 구간(칸 안에서는 스크롤을 따라 움직이므로 칸 전체)을 오늘 선이 지나는가
  const from = fits ? cell.left : cell.left + cell.width
  const crossed = todayX >= from && todayX <= from + (fits ? cell.width : textW)
  return (
    <span className={`gantt-cell-text${fits ? '' : ' beside'}${crossed ? ' crossed' : ''}`} style={fits ? cell : { left: cell.left + cell.width }}>
      <span className="gantt-whole-text">{text}</span>
    </span>
  )
}
