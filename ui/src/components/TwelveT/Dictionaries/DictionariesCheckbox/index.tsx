import React, { useEffect, useState } from 'react'
import { Checkbox } from 'antd'
import { getDictionariesType } from './service'
import { system } from '@/utils/twelvet'

// antd 5.29.3 起 antd/es/checkbox/Group 不再导出 CheckboxValueType
// （Group 已改为泛型 CheckboxGroupProps<T>），故按该类型的原始定义就地声明，
// 而不是用 any 丢掉类型安全。
type CheckboxValueType = string | number | boolean

/**
 * 字典模块数据管理类型选择器
 */
const DictionariesCheckbox: React.FC<{
    type: string
    onChange?: (checkedValue: Array<CheckboxValueType>) => void
}> = (props) => {

    const [treeData, setTreeData] = useState<any>([])

    const { type } = props

    const makeTree = async () => {
        try {
            const { data } = await getDictionariesType(type)

            // 制作数据
            const tree: any = []
            data.map((item: {
                dictCode: number
                dictValue: string
                dictLabel: string
            }) => {
                tree.push(
                    { label: item.dictLabel, value: item.dictValue }
                )
                return false
            })

            setTreeData(tree)

        } catch (e) {
            system.error(e)
        }
    }

    useEffect(() => {
        makeTree()
    }, [])

    return (
        <Checkbox.Group
            {...props}
            options={treeData}
        />
    )

}

export default DictionariesCheckbox
